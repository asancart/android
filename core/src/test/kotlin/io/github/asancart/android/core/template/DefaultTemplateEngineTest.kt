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
        val r = engine.run(keyword, "\u062D\u0633\u0627\u06281122334455\n\u0648\u0627\u0631\u06CC\u06322,750,000", sender = "BANK X")
        assertEquals(TemplateStatus.DEPOSIT, r.status)
        assertEquals(2_750_000L, r.amount)
        assertEquals("1122334455", r.account)
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

    private val noDigits = NormalizeFlags(digits = false)

    @Test
    fun `with digit normalization off a Persian or Devanagari digit is not backslash d (REQ-DEV-041)`() {
        val amount = t(pattern = "^\\u0645\\u0628\\u0644\\u063A (?<amount>\\d+)$", direction = Direction.Fixed(deposit = true)).copy(normalize = noDigits)
        assertEquals(TemplateStatus.DEPOSIT, engine.run(amount, "\u0645\u0628\u0644\u063A 500").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(amount, "\u0645\u0628\u0644\u063A \u06F5\u06F0\u06F0").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(amount, "\u0645\u0628\u0644\u063A \u0665\u0660\u0660").status)
        // No step turns Devanagari digits into Latin ones, so they are never backslash d.
        assertEquals(TemplateStatus.NO_MATCH, engine.run(amount.copy(normalize = NormalizeFlags()), "\u0645\u0628\u0644\u063A \u096B\u0966\u0966").status)
        // With digits on (the default) the Persian amount is Latin before matching.
        assertEquals(500L, engine.run(amount.copy(normalize = NormalizeFlags()), "\u0645\u0628\u0644\u063A \u06F5\u06F0\u06F0").amount)
    }

    @Test
    fun `a pattern outside the dialect is INVALID_TEMPLATE, with the tokens as detail`() {
        val r = engine.run(t(pattern = "^(?<kind>\\w+) (?<amount>\\d+)$"), "\u0648\u0627\u0631\u06CC\u0632 5")
        assertEquals(TemplateStatus.INVALID_TEMPLATE, r.status)
        assertEquals("outside the regex dialect: \\w", r.detail)
    }

    @Test
    fun `the always-on whitespace steps apply even with every flag off (REQ-DEV-050)`() {
        val off = NormalizeFlags(bidiControls = false, arabicLetters = false, digits = false, punctuation = false, whitespace = false, dropBlankLines = false)
        val p = t(pattern = "^(?<kind>\\S+) (?<amount>\\d+)\\n.$", direction = Direction.Keyword(listOf("\u0648\u0627\u0631\u06CC\u0632"), emptyList())).copy(normalize = off)
        assertEquals(TemplateStatus.DEPOSIT, engine.run(p, "\u0648\u0627\u0631\u06CC\u0632\u00A0500\r\nx").status)
        assertEquals(TemplateStatus.DEPOSIT, engine.run(p, "\uFEFF\u0648\u0627\u0631\u06CC\u0632\u2007500\u2028x").status)
        // `.` doesn't match a line break of any kind: they are all `\n` by then.
        assertEquals(TemplateStatus.NO_MATCH, engine.run(p, "\u0648\u0627\u0631\u06CC\u0632 500\n\u0085").status)
    }

    private fun toman(pattern: String = "^(?<amount>[\\d,]+)$") = ParserTemplate(
        name = "x",
        senderPatterns = listOf("700717"),
        pattern = pattern,
        direction = Direction.Fixed(deposit = true),
        priority = 1,
        amountUnit = AmountUnit.TOMAN,
    )

    @Test
    fun `REQ-DEV-054 - the rials after the toman step are at most 2^53 - 1, the exact product (T2-19)`() {
        assertEquals(9_007_199_254_740_991L, DefaultTemplateEngine.parseAmount("9007199254740991"))
        assertEquals(null, DefaultTemplateEngine.parseAmount("9007199254740992"))
        assertEquals(9_007_199_254_740_990L, DefaultTemplateEngine.parseAmount("900719925474099", AmountUnit.TOMAN))
        assertEquals(null, DefaultTemplateEngine.parseAmount("900719925474100", AmountUnit.TOMAN))
        // The admin engine once made this 90071992547409900 rials (digits lost) and the server 90071992547409910.
        assertEquals(null, DefaultTemplateEngine.parseAmount("9007199254740991", AmountUnit.TOMAN))
        assertEquals(null, DefaultTemplateEngine.parseAmount("99999999999999999999999", AmountUnit.TOMAN))
        assertEquals(null, DefaultTemplateEngine.parseAmount("9999999999999999", AmountUnit.TOMAN)) // 16 digits, × 10 in a Long
        assertEquals(9_007_199_254_740_990L, DefaultTemplateEngine.parseAmount("000900,719,925,474,099", AmountUnit.TOMAN))
        assertEquals(50_000L, DefaultTemplateEngine.parseAmount("5,000", AmountUnit.TOMAN))
        assertEquals(DefaultTemplateEngine.MAX_RIALS, (1L shl 53) - 1)
    }

    @Test
    fun `REQ-DEV-054 - a template run gives BAD_AMOUNT above the bound and DEPOSIT at it`() {
        val bad = engine.run(toman(), "900719925474100")
        assertEquals(TemplateStatus.BAD_AMOUNT, bad.status)
        assertEquals(true, bad.matched)
        assertEquals(null, bad.direction)
        assertEquals(null, bad.amount)
        val ok = engine.run(toman(), "900719925474099")
        assertEquals(TemplateStatus.DEPOSIT, ok.status)
        assertEquals(9_007_199_254_740_990L, ok.amount)
        val rial = toman().copy(amountUnit = AmountUnit.RIAL)
        assertEquals(TemplateStatus.DEPOSIT, engine.run(rial, "9,007,199,254,740,991").status)
        assertEquals(TemplateStatus.BAD_AMOUNT, engine.run(rial, "9,007,199,254,740,992").status)
    }
}
