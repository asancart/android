package io.github.asancart.android.core.template

import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.ParserTemplateItem
import io.github.asancart.android.core.deviceapi.ParserTemplatesResponse
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TemplateSyncTest {
    private val t = Instant.parse("2026-11-12T00:00:00Z")
    private val body = """{"schema_version":1,"name":"x-a","sender_patterns":["X"],"pattern":"^(?<amount>\\d+)$","direction":{"mode":"fixed","value":"in"},"priority":10}"""

    private fun item(id: String, status: ParserTemplateItem.Status = ParserTemplateItem.Status.PUBLISHED, b: String? = body, v: Int = 1) =
        ParserTemplateItem(id, "x", "x-a", v, status, t, null, if (status == ParserTemplateItem.Status.RETIRED) null else b)

    private fun response(vararg items: ParserTemplateItem) = ParserTemplatesResponse(t.plusSeconds(5), items.toList())

    @Test
    fun `published stores or replaces in place, retired deletes, twice is harmless (REQ-DEV-006)`() {
        val a = TemplateSync.apply(emptyList(), response(item("1"), item("2")))
        assertEquals(listOf("1", "2"), a.templates.map { it.id })
        assertEquals(t.plusSeconds(5), a.since)
        val b = TemplateSync.apply(a.templates, response(item("1", v = 2), item("2", ParserTemplateItem.Status.RETIRED)))
        assertEquals(listOf("1" to 2), b.templates.map { it.id to it.version })
        assertEquals(b.templates, TemplateSync.apply(b.templates, response(item("1", v = 2), item("2", ParserTemplateItem.Status.RETIRED))).templates)
    }

    @Test
    fun `a body that fails schema v1 is skipped and the stored copy kept`() {
        val a = TemplateSync.apply(emptyList(), response(item("1")))
        val b = TemplateSync.apply(a.templates, response(item("1", b = """{"schema_version":2}""", v = 2)))
        assertEquals(listOf("1"), b.rejected)
        assertEquals(1, b.templates.single().version)
    }

    @Test
    fun `official templates stay before custom ones`() {
        val custom = StoredTemplate("custom/mine", null, 1, custom = true, template = TemplateSchema.parse(body).let { (it as TemplateSchema.Result.Valid).template })
        val a = TemplateSync.apply(listOf(custom), response(item("1")))
        assertEquals(listOf("1", "custom/mine"), a.templates.map { it.id })
    }

    @Test
    fun `whatever the stub serves is valid, and nothing new after server_time`() = runTest {
        // The official set is bundled only in local builds (scripts/sync-parser-templates.sh).
        val r = assertIs<ApiResult.Ok<ParserTemplatesResponse>>(StubDeviceApi().parserTemplates(null)).value
        val applied = TemplateSync.apply(emptyList(), r)
        assertTrue(applied.rejected.isEmpty())
        assertEquals(r.items.size, applied.templates.size)
        val later = assertIs<ApiResult.Ok<ParserTemplatesResponse>>(StubDeviceApi().parserTemplates(r.serverTime)).value
        assertTrue(later.items.isEmpty())
    }

    @Test
    fun `templates are stored as JSON and come back unchanged`() {
        val samples = listOf(
            ParserTemplate(name = "x-fixed", senderPatterns = listOf("X"), pattern = "^(?<amount>\\d+)$", direction = Direction.Fixed(true), priority = 1),
            ParserTemplate(name = "x-out", senderPatterns = listOf("X", "1234"), pattern = "^[\\s\\S]*$", direction = Direction.Fixed(false), priority = 900, flags = "is"),
            ParserTemplate(name = "x-sign", senderPatterns = listOf("X"), pattern = "^(?<amount>\\d+)(?<sign>[+-])$", direction = Direction.Sign, priority = 10, amountUnit = AmountUnit.TOMAN),
            ParserTemplate(
                name = "x-kw",
                senderPatterns = listOf("X"),
                normalize = NormalizeFlags(digits = false, dropBlankLines = false),
                pattern = "^(?<kind>\\S+) (?<amount>\\d+)$",
                direction = Direction.Keyword(listOf("\u0648\u0627\u0631\u06CC\u0632"), listOf("\u0628\u0631\u062F\u0627\u0634\u062A")),
                priority = 20,
            ),
        )
        for (t in samples) assertEquals(t, assertIs<TemplateSchema.Result.Valid>(TemplateSchema.parse(TemplateSchema.toJson(t))).template)
    }
}
