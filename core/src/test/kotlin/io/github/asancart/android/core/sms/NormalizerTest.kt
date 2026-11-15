package io.github.asancart.android.core.sms

import io.github.asancart.android.core.template.AmountUnit
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.NormalizeFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-DEV-050, REQ-DEV-051, REQ-DEV-054; cases from the reference engine's own tests. */
class NormalizerTest {
    private fun n(s: String, flags: NormalizeFlags = NormalizeFlags()) = Normalizer.normalizeText(s, flags)

    @Test
    fun `Arabic letters become Persian`() {
        assertEquals("\u0628\u0627\u0646\u06A9 \u0645\u0644\u06CC \u0627\u06CC\u0631\u0627\u0646", n("\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646"))
        assertEquals("\u0645\u0648\u0633\u06CC", n("\u0645\u0648\u0633\u0649"))
    }

    @Test
    fun `Persian and Arabic-Indic digits become Latin`() {
        assertEquals("12:34 1404.11.22", n("\u06F1\u06F2:\u06F3\u06F4 \u06F1\u06F4\u06F0\u06F4.\u06F1\u06F1.\u06F2\u06F2"))
        assertEquals("3/4-1:20", n("\u0663/\u0664-\u0661:\u0662\u0660"))
    }

    @Test
    fun `thousands separator, fullwidth colon and NBSP`() {
        assertEquals("17,280,000\n\u062D\u0633\u0627\u0628:1400", n("17\u066C280\u066C000\n\u062D\u0633\u0627\u0628\uFF1A1400"))
        assertEquals("a b", n("a\u00A0b"))
    }

    @Test
    fun `direction marks and BOM are removed, ZWNJ is kept`() {
        assertEquals("+50,000,000", n("\u200E+50,000,000\u200E"))
        assertEquals("1111-222-3333333-4", n("\u202A1111-222-3333333-4\u202C"))
        assertEquals("\u0628\u0644\u0648", n("\uFEFF\u0628\u0644\u0648"))
        assertEquals("\u0645\u06CC\u200C\u0634\u0648\u062F", n("\u0645\u06CC\u200C\u0634\u0648\u062F"))
    }

    @Test
    fun `lines are trimmed, runs collapsed, blank lines dropped, line breaks unified`() {
        assertEquals("*a*\nb: 1\nc 5", n("*a*  \r\nb: 1  \n\n\nc  5 \r"))
        assertEquals("a\nb\nc", n("a\u2028b\u2029c"))
    }

    private val allOff = NormalizeFlags(
        bidiControls = false,
        arabicLetters = false,
        digits = false,
        punctuation = false,
        whitespace = false,
        dropBlankLines = false,
    )

    @Test
    fun `line breaks always become a newline, whatever the flags (REQ-DEV-050)`() {
        assertEquals("a\nb\nc\nd\ne\nf", n("a\rb\r\nc\u0085d\u2028e\u2029f", allOff))
        assertEquals("a\n\nb", n("a\r\rb", allOff))
        assertEquals("a\n\nb", n("a\n\r\nb", allOff))
    }

    @Test
    fun `odd whitespace always becomes a space and U+FEFF is removed, whatever the flags`() {
        val odd = listOf('\u000B', '\u000C', '\u00A0', '\u1680', '\u202F', '\u205F', '\u3000') + ('\u2000'..'\u200A')
        assertEquals(18, odd.size)
        for (c in odd) assertEquals("a b", n("a${c}b", allOff), "U+%04X".format(c.code))
        assertEquals("ab", n("a\uFEFFb", allOff))
        assertEquals("\u0628\u0644\u0648", n("\uFEFF\u0628\u0644\u0648", allOff))
        // Not whitespace: zero-width space, ZWNJ, direction marks and tab are left to the flags.
        assertEquals("a\u200Bb\u200Cc\u200Ed\te", n("a\u200Bb\u200Cc\u200Ed\te", allOff))
        assertEquals("a\u200Bb\u200Ccd e", n("a\u200Bb\u200Cc\u200Ed\te"))
    }

    @Test
    fun `a character outside the BMP or a lone surrogate always becomes U+FFFD (T2-16)`() {
        val emoji = String(Character.toChars(0x1F600))
        assertEquals("a\uFFFDb\uFFFD", n("a${emoji}b$emoji", allOff))
        assertEquals("\uFFFD\uFFFDx\uFFFD", n(Char(0xD800).toString() + Char(0xDC00) + Char(0xDC00) + "x" + Char(0xDBFF), allOff))
        assertEquals("\uFFFD", n("\uFFFD", allOff))
    }

    @Test
    fun `the always-on steps run first, so later steps see one kind of line break and space`() {
        assertEquals("a b\nc", n("a\u00A0\u3000b\u2028\u2028c"))
        assertEquals("a\nb", n("a\u0085\u0085b", NormalizeFlags(whitespace = false)))
        assertEquals("12", Normalizer.alwaysOn("12"))
    }

    @Test
    fun `disabled flags are respected`() {
        assertEquals("\u0628\u0627\u0646\u0643 \u06F1", n("\u0628\u0627\u0646\u0643 \u06F1", NormalizeFlags(arabicLetters = false, digits = false)))
        assertEquals("a\n\nb", n("a\n\nb", NormalizeFlags(dropBlankLines = false)))
    }

    @Test
    fun `numeric senders keep the last 10 digits`() {
        assertEquals("9999987641", Normalizer.normalizeSender("+989999987641"))
        assertEquals("9999987641", Normalizer.normalizeSender("0999 998 7641"))
        assertEquals("98700719", Normalizer.normalizeSender("98700719"))
        assertEquals("9898700719", Normalizer.normalizeSender("+98 9870 0719"))
        assertEquals("700717", Normalizer.normalizeSender("\u06F7\u06F0\u06F0\u06F7\u06F1\u06F7"))
    }

    @Test
    fun `text senders ignore case and spaces, empty never matches`() {
        assertEquals(Normalizer.normalizeSender("Bank Mellat"), Normalizer.normalizeSender("BANKMELLAT"))
        assertNotEquals(Normalizer.normalizeSender("09830009417"), Normalizer.normalizeSender("+98 983 000 9419"))
        assertTrue(Normalizer.senderMatches(listOf("Bank Mellat"), "bank mellat"))
        assertTrue(!Normalizer.senderMatches(listOf(""), " "))
    }

    @Test
    fun `amounts strip separators, reject non-integers and convert toman`() {
        assertEquals(2_750_000L, DefaultTemplateEngine.parseAmount("2,750,000"))
        assertEquals(17_280_000L, DefaultTemplateEngine.parseAmount("17\u066C280\u066C000"))
        assertEquals(1_000_000L, DefaultTemplateEngine.parseAmount("\u06F1\u06F0\u06F0\u06F0\u06F0\u06F0\u06F0"))
        assertNull(DefaultTemplateEngine.parseAmount("12.5"))
        assertNull(DefaultTemplateEngine.parseAmount("0"))
        assertNull(DefaultTemplateEngine.parseAmount(""))
        assertNull(DefaultTemplateEngine.parseAmount("9007199254740992"))
        assertEquals(50_000L, DefaultTemplateEngine.parseAmount("5,000", AmountUnit.TOMAN))
    }
}
