package io.github.asancart.android.core.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.doubleOrNull
import io.github.asancart.android.core.sms.Normalizer

/**
 * Parser Template schema v1 validation (REQ-DEV-040..042): a port of the reference validator
 * (asancart/frontend apps/admin/src/templates/schema.ts `validateTemplateBody`), with the same
 * error paths and codes. A body the admin panel accepts is accepted here and the other way round.
 */
object TemplateSchema {
    const val SCHEMA_VERSION = 1

    val TOP_LEVEL_KEYS = listOf("schema_version", "name", "sender_patterns", "normalize", "pattern", "flags", "direction", "amount_unit", "priority")
    val NORMALIZE_KEYS = listOf("bidi_controls", "arabic_letters", "digits", "punctuation", "whitespace", "drop_blank_lines")

    /** Named groups the schema allows (REQ-DEV-041). */
    val CAPTURE_GROUPS = listOf("amount", "account", "reference", "sign", "kind", "channel")

    private val NAME_RE = Regex("^[a-z0-9][a-z0-9-]*$")
    private val NAMED_GROUP = Regex("""\(\?<([A-Za-z_][A-Za-z0-9_]*)>""")

    enum class Code { JSON, TYPE, REQUIRED, UNKNOWN_FIELD, SCHEMA_VERSION, EMPTY, NAME_FORMAT, NOT_ANCHORED, REGEX, AMOUNT_GROUP, UNKNOWN_GROUP, MISSING_GROUP, FLAGS, ENUM, INTEGER, KEYWORD_EMPTY }

    /** [path] as in the reference: `direction.in`, `sender_patterns[1]`, `""` for the whole body. */
    data class Error(val path: String, val code: Code, val detail: String? = null)

    sealed interface Result {
        data class Valid(val template: ParserTemplate) : Result

        data class Invalid(val errors: List<Error>) : Result
    }

    /** Named groups declared in a pattern, in order. */
    fun namedGroups(pattern: String): List<String> = NAMED_GROUP.findAll(pattern).map { it.groupValues[1] }.toList()

    fun isAnchored(pattern: String): Boolean = pattern.startsWith("^") && pattern.endsWith("$") && !pattern.endsWith("\\$")

    /** Parse JSON text, then validate. */
    fun parse(text: String): Result {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return Result.Invalid(listOf(Error("", Code.JSON, e.message)))
        }
        return validate(element)
    }

    fun validate(input: JsonElement): Result {
        val errors = mutableListOf<Error>()
        fun err(path: String, code: Code, detail: String? = null) {
            errors += Error(path, code, detail)
        }

        if (input !is JsonObject) return Result.Invalid(listOf(Error("", Code.TYPE)))
        for (k in input.keys) if (k !in TOP_LEVEL_KEYS) err(k, Code.UNKNOWN_FIELD, k)

        if (input["schema_version"].number() != SCHEMA_VERSION.toDouble()) err("schema_version", Code.SCHEMA_VERSION)

        val name = input["name"]
        when {
            name == null -> err("name", Code.REQUIRED)
            name.string() == null -> err("name", Code.TYPE)
            !NAME_RE.matches(name.string()!!) -> err("name", Code.NAME_FORMAT)
        }

        val senders = input["sender_patterns"]
        when {
            senders == null -> err("sender_patterns", Code.REQUIRED)
            senders !is JsonArray -> err("sender_patterns", Code.TYPE)
            senders.isEmpty() -> err("sender_patterns", Code.EMPTY)
            else -> senders.forEachIndexed { i, s ->
                val v = s.string()
                if (v == null) err("sender_patterns[$i]", Code.TYPE) else if (Normalizer.jsTrim(v).isEmpty()) err("sender_patterns[$i]", Code.EMPTY)
            }
        }

        val normalize = input["normalize"]
        if (normalize != null) {
            if (normalize !is JsonObject) {
                err("normalize", Code.TYPE)
            } else {
                for ((k, v) in normalize) {
                    if (k !in NORMALIZE_KEYS) err("normalize.$k", Code.UNKNOWN_FIELD, k) else if (v.boolean() == null) err("normalize.$k", Code.TYPE)
                }
            }
        }

        // `input.flags ?? ""`: missing and null both mean no flags.
        val flagsElement = input["flags"]?.takeUnless { it is JsonNull }
        val flags = if (flagsElement == null) "" else flagsElement.string()
        val flagsOk = flags != null && flags.all { it == 'i' || it == 's' } && flags.toSet().size == flags.length
        if (!flagsOk) err("flags", Code.FLAGS)

        val dir = input["direction"]
        val dirObj = dir as? JsonObject
        val mode = dirObj?.get("mode")?.string()
        val isIgnore = mode == "fixed" && dirObj["value"]?.string() == "out"

        var groups = emptyList<String>()
        val patternElement = input["pattern"]
        val pattern = patternElement?.string()
        when {
            patternElement == null -> err("pattern", Code.REQUIRED)
            pattern == null -> err("pattern", Code.TYPE)
            pattern.isEmpty() -> err("pattern", Code.EMPTY)
            else -> {
                if (!isAnchored(pattern)) err("pattern", Code.NOT_ANCHORED)
                try {
                    PortableRegex.compile(pattern, if (flagsOk) flags!! else "")
                } catch (e: PortableRegex.InvalidPattern) {
                    err("pattern", Code.REGEX, e.message)
                }
                groups = namedGroups(pattern)
                if (!isIgnore && "amount" !in groups) err("pattern", Code.AMOUNT_GROUP)
                for (g in groups) if (g !in CAPTURE_GROUPS) err("pattern", Code.UNKNOWN_GROUP, g)
            }
        }

        when {
            dir == null -> err("direction", Code.REQUIRED)
            dirObj == null -> err("direction", Code.TYPE)
            mode == "fixed" -> {
                val v = dirObj["value"]?.string()
                if (v != "in" && v != "out") err("direction.value", Code.ENUM)
            }
            mode == "sign" -> if (pattern != null && "sign" !in groups) err("direction", Code.MISSING_GROUP, "sign")
            mode == "keyword" -> {
                if (pattern != null && "kind" !in groups) err("direction", Code.MISSING_GROUP, "kind")
                for (key in listOf("in", "out")) {
                    when (val v = dirObj[key]) {
                        null -> err("direction.$key", Code.REQUIRED)
                        !is JsonArray -> err("direction.$key", Code.TYPE)
                        else -> v.forEachIndexed { i, w ->
                            val s = w.string()
                            if (s == null) err("direction.$key[$i]", Code.TYPE) else if (Normalizer.jsTrim(s).isEmpty()) err("direction.$key[$i]", Code.EMPTY)
                        }
                    }
                }
                val incoming = dirObj["in"]
                if (incoming is JsonArray && incoming.isEmpty()) err("direction.in", Code.KEYWORD_EMPTY)
            }
            else -> err("direction.mode", Code.ENUM)
        }

        val unit = input["amount_unit"]
        if (unit != null && unit.string() != "rial" && unit.string() != "toman") err("amount_unit", Code.ENUM)

        val priority = input["priority"]
        val p = priority.number()
        when {
            priority == null -> err("priority", Code.REQUIRED)
            p == null || p != Math.floor(p) || p.isInfinite() || p < 0 || p > 1000 -> err("priority", Code.INTEGER)
        }

        if (errors.isNotEmpty()) return Result.Invalid(errors)
        return Result.Valid(toTemplate(input))
    }

    private fun toTemplate(o: JsonObject): ParserTemplate {
        val n = o["normalize"] as? JsonObject
        fun flag(k: String) = n?.get(k)?.boolean() ?: true
        val d = o["direction"] as JsonObject
        val direction = when (d["mode"]!!.string()) {
            "fixed" -> Direction.Fixed(deposit = d["value"]!!.string() == "in")
            "sign" -> Direction.Sign
            else -> Direction.Keyword(
                incoming = (d["in"] as JsonArray).map { it.string()!! },
                outgoing = (d["out"] as JsonArray).map { it.string()!! },
            )
        }
        return ParserTemplate(
            schemaVersion = SCHEMA_VERSION,
            name = o["name"]!!.string()!!,
            senderPatterns = (o["sender_patterns"] as JsonArray).map { it.string()!! },
            normalize = NormalizeFlags(
                bidiControls = flag("bidi_controls"),
                arabicLetters = flag("arabic_letters"),
                digits = flag("digits"),
                punctuation = flag("punctuation"),
                whitespace = flag("whitespace"),
                dropBlankLines = flag("drop_blank_lines"),
            ),
            pattern = o["pattern"]!!.string()!!,
            flags = o["flags"]?.takeUnless { it is JsonNull }?.string() ?: "",
            direction = direction,
            amountUnit = if (o["amount_unit"]?.string() == "toman") AmountUnit.TOMAN else AmountUnit.RIAL,
            priority = o["priority"].number()!!.toInt(),
        )
    }

    /** Schema v1 JSON of [t], the inverse of [validate] for a valid body. */
    fun toJson(t: ParserTemplate): String = buildJsonObject {
        put("schema_version", t.schemaVersion)
        put("name", t.name)
        putJsonArray("sender_patterns") { t.senderPatterns.forEach { add(it) } }
        putJsonObject("normalize") {
            put("bidi_controls", t.normalize.bidiControls)
            put("arabic_letters", t.normalize.arabicLetters)
            put("digits", t.normalize.digits)
            put("punctuation", t.normalize.punctuation)
            put("whitespace", t.normalize.whitespace)
            put("drop_blank_lines", t.normalize.dropBlankLines)
        }
        put("pattern", t.pattern)
        put("flags", t.flags)
        putJsonObject("direction") {
            when (val d = t.direction) {
                is Direction.Fixed -> {
                    put("mode", "fixed")
                    put("value", if (d.deposit) "in" else "out")
                }
                Direction.Sign -> put("mode", "sign")
                is Direction.Keyword -> {
                    put("mode", "keyword")
                    putJsonArray("in") { d.incoming.forEach { add(it) } }
                    putJsonArray("out") { d.outgoing.forEach { add(it) } }
                }
            }
        }
        put("amount_unit", if (t.amountUnit == AmountUnit.TOMAN) "toman" else "rial")
        put("priority", t.priority)
    }.toString()

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonElement?.boolean(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
