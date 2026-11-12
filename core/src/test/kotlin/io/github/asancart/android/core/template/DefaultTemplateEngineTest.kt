package io.github.asancart.android.core.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-DEV-052..056 statuses and the set rules; cases from the reference engine's tests. */
class DefaultTemplateEngineTest {
    private val engine = DefaultTemplateEngine()

    private fun t(
        name: String = "x-deposit",
        pattern: String,
        direction: Direction = Direction.Keyword(listOf("\u0648\u0627\u0631\u06CC\u0632"), listOf("\u0628\u0631\u062F\u0627\u0634\u062A")),
        priority: Int = 10,
        senders: List<String> = listOf("Bank X"),
        unit: AmountUnit = AmountUnit.RIAL,
    ) = ParserTemplate(name = name, senderPatterns = senders, pattern = pattern, direction = direction, priority = priority, amountUnit = unit)

    private val keyword = t(pattern = "^\u062D\u0633\u0627\u0628(?<account>\\d+)\\n(?<kind>[^\\d\\n]+)(?<amount>[\\d,]+)$")

    @Test
    fun `deposit with fields, withdrawal ignored by keyword`() {
        val r = engine.run(keyword, "\u062D\u0633\u0627\u06288065955489\n\u0648\u0627\u0631\u06CC\u06322,250,000", sender = "BANK X")
        assertEquals(TemplateStatus.DEPOSIT, r.status)
        assertEquals(2_250_000L, r.amount)
        assertEquals("8065955489", r.account)
        assertEquals(TemplateStatus.IGNORED, engine.run(keyword, "\u062D\u0633\u0627\u06281\n\u0628\u0631\u062F\u0627\u0634\u062A500").status)
    }

    @Test
    fun `keyword direction ignores Arabic and Persian letter differences, unknown keyword is DIRECTION_UNKNOWN`() {
        val ar = t(pattern = "^(?<kind>\\S+) (?<amount>\\d+)$", direction = Direction.Keyword(listOf("\u0648\u0627\u0631\u064A\u0632"), listOf("\u0628\u0631\u062F\u0627\u0634\u062A")))
        assertEquals(TemplateStatus.DEPOSIT, engine.run(ar, "\u0648\u0627\u0631\u06CC\u0632 100").status)
        assertEquals(TemplateStatus.DIRECTION_UNKNOWN, engine.run(ar, "\u0634\u0628\u0627 100").status)
    }

    @Test
    fun `sign direction`() {
        val s = t(pattern = "^(?<amount>[\\d,]+)(?<sign>[+-])$", direction = Direction.Sign)
        assertEquals(TemplateStatus.DEPOSIT, engine.run(s, "5,000+").status)
        assertEquals(TemplateStatus.IGNORED, engine.run(s, "5,000-").status)
    }

    @Test
    fun `bad amount, missing amount, invalid template, sender mismatch, no match`() {
        assertEquals(TemplateStatus.BAD_AMOUNT, engine.run(t(pattern = "^(?<kind>\\S+) (?<amount>[\\d.]+)$"), "\u0648\u0627\u0631\u06CC\u0632 12.5").status)
        assertEquals(TemplateStatus.BAD_AMOUNT, engine.run(t(pattern = "^(?<kind>\\S+) [\\d]+$"), "\u0648\u0627\u0631\u06CC\u0632 12").status)
        val invalid = engine.run(t(pattern = "^(?<amount>[0-9]+$"), "12")
        assertEquals(TemplateStatus.INVALID_TEMPLATE, invalid.status)
        assertTrue(!invalid.matched)
        assertEquals(TemplateStatus.SENDER_MISMATCH, engine.run(keyword, "\u062D\u0633\u0627\u06281\n\u0648\u0627\u0631\u06CC\u06325", sender = "Bank Y").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(keyword, "\u062D\u0633\u0627\u06281\n\u0648\u0627\u0631\u06CC\u06325\n\u0645\u0627\u0646\u062F\u06479", sender = " ").status)
    }

    @Test
    fun `toman amounts are converted to rials`() {
        val toman = t(pattern = "^(?<kind>\\S+) (?<amount>[\\d,]+)$", unit = AmountUnit.TOMAN)
        assertEquals(50_000L, engine.run(toman, "\u0648\u0627\u0631\u06CC\u0632 5,000").amount)
    }

    @Test
    fun `missing required field is flagged on a deposit (DEC-011)`() {
        val r = engine.run(keyword, "\u062D\u0633\u0627\u06281\n\u0648\u0627\u0631\u06CC\u06325", requiredFields = listOf("reference"))
        assertEquals(TemplateStatus.DEPOSIT, r.status)
        assertEquals(listOf("reference"), r.missingRequired)
    }

    @Test
    fun `first match in priority order wins, ties keep the given order, no match goes to unparsed`() {
        val ignore = t(name = "x-otp", pattern = "^[\\s\\S]*\u0631\u0645\u0632[\\s\\S]*$", direction = Direction.Fixed(deposit = false), priority = 900)
        val dep = t(name = "x-a", pattern = "^\u0631\u0645\u0632 (?<amount>\\d+)$", direction = Direction.Fixed(deposit = true), priority = 10)
        val dep2 = t(name = "x-b", pattern = "^\u0631\u0645\u0632 (?<amount>\\d+)$", direction = Direction.Fixed(deposit = true), priority = 10)
        val set = engine.runSet(listOf(ignore, dep2, dep), "\u0631\u0645\u0632 12")
        assertEquals(listOf(1, 2, 0), set.order)
        assertEquals("x-b", set.outcome?.templateName)
        assertEquals(TemplateStatus.IGNORED, engine.runSet(listOf(ignore, dep), "\u0631\u0645\u0632 \u06CC\u06A9").outcome?.status)
        assertNull(engine.runSet(listOf(dep), "\u0633\u0644\u0627\u0645").outcome)
    }

    @Test
    fun `an empty optional capture is no capture`() {
        val r = engine.run(t(pattern = "^(?<kind>\\S+) (?<amount>\\d+)(?<reference>\\d*)$"), "\u0648\u0627\u0631\u06CC\u0632 5")
        assertNull(r.reference)
    }
}
