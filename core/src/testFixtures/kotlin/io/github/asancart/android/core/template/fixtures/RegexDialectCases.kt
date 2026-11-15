package io.github.asancart.android.core.template.fixtures

import io.github.asancart.android.core.sms.Normalizer
import io.github.asancart.android.core.template.PortableRegex
import io.github.asancart.android.core.template.TemplateSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * REQ-DEV-041 regex dialect cases (`regex-dialect-cases.json`, next to this class): every allowed
 * construct with how it matches, every refused token, and in-dialect patterns that don't compile.
 * REQ-DEV-041 names this file as the shared case set every engine runs (T2.16); the Device engine's
 * owner keeps it in line with the doc. Here the cases run on the JVM (:core) and on Android's ICU
 * regex (app androidTest), and `scripts/check-dialect-cases.mjs` checks the expectations against
 * JavaScript `RegExp`, the admin engine's library. No SMS data.
 */
class RegexDialectCases(json: String = resource()) {
    private val cases: JsonObject = Json.parseToJsonElement(json).jsonObject

    data class Report(val allowed: Int, val refused: Int, val regex: Int, val failures: List<String>) {
        override fun toString(): String =
            "dialect cases: allowed $allowed, refused $refused, regex $regex" + if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n")
    }

    fun run(): Report {
        val failures = mutableListOf<String>()
        val allowed = cases["allowed"]!!.jsonArray
        val refused = cases["refused"]!!.jsonArray
        val regex = cases["regex"]!!.jsonArray

        for (e in allowed) {
            val c = e.jsonObject
            val pattern = c.string("pattern")!!
            val flags = c.string("flags") ?: ""
            fun bad(m: String) {
                failures += "allowed ${show(pattern)}: $m"
            }
            val r = try {
                PortableRegex.compile(pattern, flags)
            } catch (x: PortableRegex.InvalidPattern) {
                bad("refused: ${x.message}")
                continue
            }
            for (t in c.strings("match")) if (r.matchWhole(Normalizer.alwaysOn(t)) == null) bad("${show(t)} should match")
            for (t in c.strings("noMatch")) if (r.matchWhole(Normalizer.alwaysOn(t)) != null) bad("${show(t)} should not match")
            for (g in (c["groups"] as? JsonArray).orEmpty()) {
                val text = g.jsonObject.string("text")!!
                val m = r.matchWhole(Normalizer.alwaysOn(text)) ?: continue
                for ((k, v) in g.jsonObject["named"]!!.jsonObject) {
                    val want = (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
                    if (m[k] != want) bad("on ${show(text)}: group $k is ${m[k]?.let(::show)}, expected ${want?.let(::show)}")
                }
            }
            val errors = schemaErrors(pattern, flags)
            if (errors.isNotEmpty()) bad("schema errors $errors")
        }

        for (e in refused) {
            val c = e.jsonObject
            val pattern = c.string("pattern")!!
            val tokens = c.strings("tokens")
            fun bad(m: String) {
                failures += "refused ${show(pattern)}: $m"
            }
            val found = PortableRegex.dialectViolations(pattern)
            if (found != tokens) bad("tokens ${found.map(::show)}, expected ${tokens.map(::show)}")
            try {
                PortableRegex.compile(pattern)
                bad("compiled")
            } catch (x: PortableRegex.DialectViolation) {
                if (x.tokens != tokens) bad("exception tokens ${x.tokens.map(::show)}")
            } catch (x: PortableRegex.InvalidPattern) {
                bad("code regex instead of dialect: ${x.message}")
            }
            val errors = schemaErrors(pattern, "")
            val dialect = errors.filter { it.code == TemplateSchema.Code.DIALECT }
            if (dialect.map { it.detail } != tokens || dialect.any { it.path != "pattern" }) bad("schema dialect errors $dialect")
            if (errors.any { it.code == TemplateSchema.Code.REGEX }) bad("schema also reports regex: $errors")
        }

        for (e in regex) {
            val c = e.jsonObject
            val pattern = c.string("pattern")!!
            fun bad(m: String) {
                failures += "regex ${show(pattern)} (${c.string("why")}): $m"
            }
            val found = PortableRegex.dialectViolations(pattern)
            if (found.isNotEmpty()) bad("dialect tokens $found")
            try {
                PortableRegex.compile(pattern)
                bad("compiled")
            } catch (x: PortableRegex.DialectViolation) {
                bad("code dialect instead of regex: ${x.tokens}")
            } catch (x: PortableRegex.InvalidPattern) {
                // Expected: the engine's own message, e.g. "nothing to repeat".
                if (x.message.isNullOrEmpty()) bad("no message")
            }
            val codes = schemaErrors(pattern, "").map { it.code }
            if (TemplateSchema.Code.REGEX !in codes || TemplateSchema.Code.DIALECT in codes) bad("schema codes $codes")
        }

        return Report(allowed.size, refused.size, regex.size, failures)
    }

    /** Pattern errors of a minimal ignore template around [pattern] (no `amount` group needed). */
    private fun schemaErrors(pattern: String, flags: String): List<TemplateSchema.Error> {
        val body = buildJsonObject {
            put("schema_version", 1)
            put("name", "dialect-case")
            putJsonArray("sender_patterns") { add(JsonPrimitive("Bank X")) }
            put("pattern", pattern)
            put("flags", flags)
            putJsonObject("direction") {
                put("mode", "fixed")
                put("value", "out")
            }
            put("priority", 10)
        }
        return when (val r = TemplateSchema.validate(body)) {
            is TemplateSchema.Result.Valid -> emptyList()
            // notAnchored, unknownGroup and the like are not what these cases are about.
            is TemplateSchema.Result.Invalid -> r.errors.filter { it.code == TemplateSchema.Code.DIALECT || it.code == TemplateSchema.Code.REGEX }
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

    private fun show(s: String): String = buildString {
        append('"')
        for (ch in s) {
            if (ch.code in 0x21..0x7E && ch != '"') append(ch) else append("\\u").append(ch.code.toString(16).padStart(4, '0'))
        }
        append('"')
    }

    companion object {
        const val FILE = "regex-dialect-cases.json"

        /** The case file, a resource of this class's package. */
        fun resource(): String =
            (RegexDialectCases::class.java.getResourceAsStream(FILE) ?: error("missing $FILE")).use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
