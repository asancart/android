package io.github.asancart.android.core.template.fixtures

import io.github.asancart.android.core.sms.Normalizer
import io.github.asancart.android.core.template.ParserTemplate
import io.github.asancart.android.core.template.TemplateEngine
import io.github.asancart.android.core.template.TemplateResult
import io.github.asancart.android.core.template.TemplateSchema
import io.github.asancart.android.core.template.TemplateStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The `parser-templates` checks of asancart/documents (REQ-PT-040, `tools/validate.mjs`) run on this
 * engine, plus the reference engine's output on every template × text pair (`oracle.json`).
 * The same suite runs as JVM unit tests (:core) and as instrumented tests on Android (:app), so
 * "all fixtures pass" holds for the regex engine each platform actually uses.
 *
 * [read] returns a file of `fixtures/parser-templates/` by name.
 */
class ParserTemplateFixtures(private val read: (String) -> String) {
    class Bank(val id: String, val requiredFields: List<String>, val templates: List<ParserTemplate>, val fixtures: List<Fixture>)

    class Fixture(val id: String, val sender: String?, val smsText: String, val expect: JsonObject)

    data class Report(
        val banks: Int,
        val templates: Int,
        val fixturesPassed: Int,
        val fixturesTotal: Int,
        val negativesPassed: Int,
        val negativesTotal: Int,
        val oraclePairsAgreeing: Int,
        val oraclePairsTotal: Int,
        val failures: List<String>,
    ) {
        override fun toString(): String =
            "banks $banks, templates $templates; fixtures $fixturesPassed/$fixturesTotal; cross-bank negatives " +
                "$negativesPassed/$negativesTotal; reference pairs $oraclePairsAgreeing/$oraclePairsTotal" +
                if (failures.isEmpty()) "" else "\n" + failures.joinToString("\n")
    }

    private fun json(name: String): JsonElement = Json.parseToJsonElement(read(name))

    private val oracle: JsonObject by lazy { json("oracle.json").jsonObject }

    /** Bank files, in the oracle's (file name) order. */
    val banks: List<Bank> by lazy {
        val ids = oracle["templates"]!!.jsonArray.map { it.jsonObject["bank"]!!.jsonPrimitive.content }.distinct()
        ids.map { id -> bank(id) }
    }

    private fun bank(id: String): Bank {
        val d = json("$id.json").jsonObject
        val templates = d["templates"]!!.jsonArray.map { body ->
            when (val r = TemplateSchema.validate(body)) {
                is TemplateSchema.Result.Valid -> r.template
                is TemplateSchema.Result.Invalid -> error("$id: ${body.jsonObject["name"]} invalid: ${r.errors}")
            }
        }
        val bank = d["bank"]!!.jsonObject
        return Bank(
            id = bank["id"]!!.jsonPrimitive.content,
            requiredFields = bank["required_fields"]!!.jsonArray.map { it.jsonPrimitive.content },
            templates = templates,
            fixtures = d["fixtures"]!!.jsonArray.map { fixture(it.jsonObject) },
        )
    }

    private fun fixture(o: JsonObject) = Fixture(
        id = o["id"]!!.jsonPrimitive.content,
        sender = (o["sender"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        smsText = o["sms_text"]!!.jsonPrimitive.content,
        expect = o["expect"]!!.jsonObject,
    )

    private val negativesBlock: List<Fixture> by lazy { json("negatives.json").jsonArray.map { fixture(it.jsonObject) } }

    fun run(engine: TemplateEngine): Report {
        val failures = mutableListOf<String>()
        var fixtures = 0
        var fixturesPass = 0

        for (b in banks) {
            for (fx in b.fixtures) {
                fixtures++
                val before = failures.size
                fun bad(m: String) {
                    failures += "${b.id}: ${fx.id}: $m"
                }
                val want = fx.expect["status"]!!.jsonPrimitive.content
                val set = engine.runSet(b.templates, fx.smsText, fx.sender, b.requiredFields)
                val r = set.outcome
                val got = r?.status?.name ?: "UNPARSED"
                if (got != want) bad("status $got != $want${if (r != null) " via ${r.templateName}" else ""}")
                if (r != null && r.status == TemplateStatus.DEPOSIT) {
                    compareField(fx.expect, "amount", r.amount?.toString(), ::bad)
                    compareField(fx.expect, "account", r.account, ::bad)
                    compareField(fx.expect, "reference", r.reference, ::bad)
                    if (r.missingRequired.isNotEmpty()) bad("missing required ${r.missingRequired}")
                    for (t in b.templates.filter { it.isIgnore }) {
                        if (engine.run(t, fx.smsText, fx.sender).matched) bad("ignore template ${t.name} matches this deposit")
                    }
                }
                if (failures.size == before) fixturesPass++
            }
        }

        // Cross-bank: no non-deposit fixture (bank files and the README "Negative fixtures" block)
        // may be a DEPOSIT in any bank's full set, sender ignored.
        val negatives = banks.flatMap { b -> b.fixtures.filter { it.expect["status"]!!.jsonPrimitive.content != "DEPOSIT" }.map { b.id to it } } +
            negativesBlock.map { "(cross-bank)" to it }
        var negativesPass = 0
        for ((home, fx) in negatives) {
            val hits = banks.mapNotNull { b ->
                engine.runSet(b.templates, fx.smsText).outcome?.takeIf { it.status == TemplateStatus.DEPOSIT }?.templateName
            }
            if (hits.isEmpty()) negativesPass++ else failures += "cross-bank: $home/${fx.id} is DEPOSIT on ${hits.joinToString()}"
        }

        // Reference engine output on every pair.
        val templates = banks.flatMap { it.templates }
        val texts = banks.flatMap { b -> b.fixtures.map { it.smsText } } + negativesBlock.map { it.smsText }
        val oracleTexts = oracle["texts"]!!.jsonArray
        if (oracleTexts.size != texts.size) failures += "oracle has ${oracleTexts.size} texts, fixtures ${texts.size}"
        texts.forEachIndexed { i, text ->
            val want = oracleTexts.getOrNull(i)?.jsonObject?.get("normalized")?.jsonPrimitive?.content
            val got = Normalizer.normalizeText(text)
            if (want != null && got != want) failures += "normalize ${oracleTexts[i].jsonObject["id"]}: differs from the reference"
        }
        val expected = HashMap<Pair<Int, Int>, JsonObject>()
        for (e in oracle["results"]!!.jsonArray) {
            val o = e.jsonObject
            expected[o["t"]!!.jsonPrimitive.int to o["x"]!!.jsonPrimitive.int] = o
        }
        var pairs = 0
        var agree = 0
        templates.forEachIndexed { ti, t ->
            texts.forEachIndexed { xi, text ->
                pairs++
                val r = engine.run(t, text)
                val want = expected[ti to xi]
                val diff = if (want == null) {
                    if (r.status == TemplateStatus.NO_MATCH) null else "${r.status} != NO_MATCH"
                } else {
                    pairDiff(want, r)
                }
                if (diff == null) agree++ else failures += "reference pair ${t.name} × ${oracleTexts.getOrNull(xi)?.jsonObject?.get("id")}: $diff"
            }
        }
        for (s in oracle["senders"]!!.jsonArray) {
            val o = s.jsonObject
            val raw = o["raw"]!!.jsonPrimitive.content
            val want = o["normalized"]!!.jsonPrimitive.content
            if (Normalizer.normalizeSender(raw) != want) failures += "sender $raw: ${Normalizer.normalizeSender(raw)} != $want"
        }

        return Report(
            banks = banks.size,
            templates = templates.size,
            fixturesPassed = fixturesPass,
            fixturesTotal = fixtures,
            negativesPassed = negativesPass,
            negativesTotal = negatives.size,
            oraclePairsAgreeing = agree,
            oraclePairsTotal = pairs,
            failures = failures,
        )
    }

    private fun compareField(expect: JsonObject, key: String, have: String?, bad: (String) -> Unit) {
        val want = (expect[key] as? JsonPrimitive)?.let { if (it.isString) it.content else it.long.toString() }
        if (want != null && want != have) bad("$key $have != $want")
        if (want == null && have != null) bad("$key captured but not in expect")
    }

    private fun pairDiff(want: JsonObject, r: TemplateResult): String? {
        val status = want["status"]!!.jsonPrimitive.content
        if (r.status.name != status) return "${r.status} != $status"
        val fields = want["fields"] as? JsonObject ?: JsonObject(emptyMap())
        val have = mapOf("amount" to r.amount?.toString(), "account" to r.account, "reference" to r.reference, "kind" to r.kind, "channel" to r.channel)
        for ((k, v) in have) {
            val w = (fields[k] as? JsonPrimitive)?.let { if (it.isString) it.content else it.long.toString() }
            if (w != v) return "$k ${if (v == null) "missing" else "differs"}"
        }
        return null
    }

}
