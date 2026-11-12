package io.github.asancart.android.core.template

import io.github.asancart.android.core.template.TemplateSchema.Code
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** REQ-DEV-040..042; cases from the reference validator's tests (schema.test.ts). */
class TemplateSchemaTest {
    private val base: JsonObject = Json.parseToJsonElement(
        """
        {"schema_version":1,"name":"mellat-account","sender_patterns":["Bank Mellat"],
         "normalize":{"digits":true},"pattern":"^حساب(?<account>\\d+)\\n(?<kind>واریز|برداشت)(?<amount>[\\d,]+)$",
         "flags":"","direction":{"mode":"keyword","in":["واریز"],"out":["برداشت"]},"amount_unit":"rial","priority":10}
        """.trimIndent(),
    ).jsonObject

    private fun with(vararg changes: Pair<String, JsonElement?>): JsonObject {
        val m = base.toMutableMap()
        for ((k, v) in changes) if (v == null) m.remove(k) else m[k] = v
        return JsonObject(m)
    }

    private fun js(s: String) = Json.parseToJsonElement(s)

    private fun codes(o: JsonElement): List<Code> = when (val r = TemplateSchema.validate(o)) {
        is TemplateSchema.Result.Valid -> emptyList()
        is TemplateSchema.Result.Invalid -> r.errors.map { it.code }
    }

    @Test
    fun `a valid body, normalize, flags and amount_unit are optional`() {
        assertEquals(emptyList(), codes(base))
        assertEquals(emptyList(), codes(with("normalize" to null, "flags" to null, "amount_unit" to null)))
        val t = assertIs<TemplateSchema.Result.Valid>(TemplateSchema.validate(base)).template
        assertEquals(Direction.Keyword(listOf("\u0648\u0627\u0631\u06CC\u0632"), listOf("\u0628\u0631\u062F\u0627\u0634\u062A")), t.direction)
        assertEquals(10, t.priority)
    }

    @Test
    fun `unanchored patterns, the m flag and invalid regex are rejected`() {
        assertTrue(Code.NOT_ANCHORED in codes(with("pattern" to JsonPrimitive("\u062D\u0633\u0627\u0628(?<amount>\\d+)"))))
        assertTrue(Code.NOT_ANCHORED in codes(with("pattern" to JsonPrimitive("^(?<amount>\\d+)\\$"))))
        assertTrue(Code.FLAGS in codes(with("flags" to JsonPrimitive("m"))))
        assertTrue(Code.FLAGS in codes(with("flags" to JsonPrimitive("ii"))))
        assertTrue(Code.REGEX in codes(with("pattern" to JsonPrimitive("^(?<amount>[0-9]+$"))))
    }

    @Test
    fun `an amount group is needed unless the template only ignores`() {
        assertTrue(Code.AMOUNT_GROUP in codes(with("pattern" to JsonPrimitive("^\u0648\u0627\u0631\u06CC\u0632[0-9,]+$"), "direction" to js("""{"mode":"fixed","value":"in"}"""))))
        assertEquals(emptyList(), codes(with("pattern" to JsonPrimitive("^\u0628\u0631\u062F\u0627\u0634\u062A[0-9,]+$"), "direction" to js("""{"mode":"fixed","value":"out"}"""))))
    }

    @Test
    fun `direction is checked against the declared groups`() {
        assertTrue(Code.MISSING_GROUP in codes(with("direction" to js("""{"mode":"sign"}"""))))
        assertTrue(Code.KEYWORD_EMPTY in codes(with("direction" to js("""{"mode":"keyword","in":[],"out":[]}"""))))
        assertTrue(Code.ENUM in codes(with("direction" to js("""{"mode":"guess"}"""))))
        assertTrue(
            Code.UNKNOWN_GROUP in codes(with("pattern" to JsonPrimitive("^(?<amount>\\d+)(?<balance>\\d+)$"), "direction" to js("""{"mode":"fixed","value":"in"}"""))),
        )
    }

    @Test
    fun `unknown fields, bad names, empty senders and bad priority are rejected`() {
        assertTrue(Code.UNKNOWN_FIELD in codes(with("extra" to JsonPrimitive(1))))
        assertTrue(Code.UNKNOWN_FIELD in codes(with("normalize" to js("""{"trim":true}"""))))
        assertTrue(Code.NAME_FORMAT in codes(with("name" to JsonPrimitive("Mellat Deposit"))))
        assertTrue(Code.EMPTY in codes(with("sender_patterns" to js("[]"))))
        assertTrue(Code.EMPTY in codes(with("sender_patterns" to js("""[" "]"""))))
        assertTrue(Code.INTEGER in codes(with("priority" to JsonPrimitive(1.5))))
        assertTrue(Code.INTEGER in codes(with("priority" to JsonPrimitive(1001))))
        assertTrue(Code.SCHEMA_VERSION in codes(with("schema_version" to JsonPrimitive(2))))
        assertTrue(Code.ENUM in codes(with("amount_unit" to JsonPrimitive("dollar"))))
        assertEquals(listOf(Code.TYPE), codes(js("[]")))
    }

    @Test
    fun `JSON errors are reported`() {
        assertEquals(Code.JSON, assertIs<TemplateSchema.Result.Invalid>(TemplateSchema.parse("{ not json")).errors.single().code)
        assertIs<TemplateSchema.Result.Valid>(TemplateSchema.parse(base.toString()))
    }

    @Test
    fun `named groups are listed in order`() {
        assertEquals(listOf("account", "kind", "amount"), TemplateSchema.namedGroups((base["pattern"] as JsonPrimitive).content))
    }
}
