package io.github.asancart.android.core.template

import io.github.asancart.android.core.template.fixtures.RegexDialectCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-DEV-041 regex dialect on the JVM; the same cases run on Android's ICU (app androidTest). */
class PortableRegexTest {
    private fun whole(p: String, text: String, flags: String = "") = PortableRegex.compile(p, flags).matchWhole(text)

    private fun java(p: String) = DialectTranslator(p).translate().java

    @Test
    fun `every dialect case agrees with the expectations checked against JavaScript RegExp`() {
        val report = RegexDialectCases().run()
        println(report)
        assertTrue(report.failures.isEmpty(), report.toString())
        assertTrue(report.allowed >= 69 && report.refused >= 134 && report.regex >= 7, report.toString())
    }

    @Test
    fun `backslash d is compiled as 0-9 and backslash D as its complement`() {
        assertEquals("^[0-9]$", java("^\\d$"))
        assertEquals("^[^0-9]$", java("^\\D$"))
        assertEquals("^[0-9\\u002c]+$", java("^[\\d,]+$"))
        assertEquals("^[^0-9]$", java("^[^\\d]$"))
        assertEquals("^[^\\u0000-\\u002f\\u003a-\\x{10FFFF}]$", java("^[^\\D]$"))
    }

    @Test
    fun `Persian, Arabic-Indic and Devanagari digits are not backslash d`() {
        for (digit in listOf("\u06F5", "\u0665", "\u096B", "\uFF15")) {
            assertNull(whole("^\\d$", digit), "U+%04X".format(digit[0].code))
            assertNull(whole("^[\\d]$", digit))
            assertNotNull(whole("^\\D$", digit))
            assertNotNull(whole("^[^\\d]$", digit))
        }
        assertNotNull(whole("^\\d$", "5"))
    }

    @Test
    fun `backslash s is ASCII whitespace`() {
        for (c in listOf(" ", "\t", "\n", "\u000B", "\u000C", "\r")) assertNotNull(whole("^\\s$", c))
        for (c in listOf("\u00A0", "\u2003", "\u3000", "\u0085", "\u2028", "\uFEFF", "\u200C")) {
            assertNull(whole("^\\s$", c), "U+%04X".format(c[0].code))
            assertNotNull(whole("^\\S$", c))
        }
    }

    @Test
    fun `named groups are numbered like JavaScript, skipping non-capturing groups and lookaheads`() {
        val r = PortableRegex.compile("^(?:x)(?=a)(?<amount>a)(b)(?!c)(?<kind>c)?$")
        assertEquals(mapOf("amount" to 1, "kind" to 3), r.groups)
        val m = assertNotNull(r.matchWhole("xab"))
        assertEquals("a", m["amount"])
        assertNull(m["kind"])
    }

    @Test
    fun `a full match, so dollar never matches before a final newline and a top-level bar is fine`() {
        assertNotNull(whole("^a$", "a"))
        assertNull(whole("^a$", "a\n"))
        assertNull(whole("^a$", "ab"))
        // The text matches ^(?:P)$ (T2.16): an alternative that ends early backtracks into the next.
        assertNotNull(whole("^a|ab$", "ab"))
        assertNotNull(whole("^a|ab$", "a"))
        assertNull(whole("^a|ab$", "b"))
    }

    @Test
    fun `refused tokens are each reported once, in order, and nothing is compiled`() {
        val e = assertFailsWith<PortableRegex.DialectViolation> { PortableRegex.compile("^\\w+(?<=x)\\w\\p{L}a*+(?i)[a&&b]$") }
        assertEquals(listOf("\\w", "(?<=", "\\p{L}", "*+", "(?i)", "&&"), e.tokens)
        // A dialect token wins over a compile error later in the pattern.
        assertIs<PortableRegex.DialectViolation>(assertFailsWith<PortableRegex.InvalidPattern> { PortableRegex.compile("^\\b(a$") })
        assertEquals(emptyList(), PortableRegex.dialectViolations("^(?<amount>[\\d,]+)\\n[\\s\\S]*?$"))
    }

    @Test
    fun `a pattern inside the dialect that doesn't compile is an InvalidPattern, not a dialect violation`() {
        for (p in listOf("^(?<amount>[0-9]+$", "^a)$", "^[a$", "^a{2,1}$", "^[z-a]$", "^a\\")) {
            val e = assertFailsWith<PortableRegex.InvalidPattern>(p) { PortableRegex.compile(p) }
            assertTrue(e !is PortableRegex.DialectViolation, p)
        }
    }

    @Test
    fun `nothing to repeat and a reused name are dialect, though the JDK compiles some (T2-16)`() {
        assertEquals(listOf("*"), PortableRegex.dialectViolations("^*a$"))
        assertEquals(listOf("{3}"), PortableRegex.dialectViolations("^a{2}{3}$"))
        assertEquals(listOf("+"), PortableRegex.dialectViolations("^a*?+$"))
        assertEquals(listOf("(?<amount>"), PortableRegex.dialectViolations("^(?<amount>a)(?<amount>b)$"))
        assertEquals(listOf("\\d-z"), PortableRegex.dialectViolations("^[\\d-z]$"))
        // A refused (? token is an opener: nothing to repeat after it. A refused escape is something to repeat.
        assertEquals(listOf("(?i)", "*"), PortableRegex.dialectViolations("^(?i)*a$"))
        assertEquals(listOf("(?<=", "+"), PortableRegex.dialectViolations("^(?<=+a)b$"))
        assertEquals(listOf("\\w"), PortableRegex.dialectViolations("^\\w+$"))
    }

    @Test
    fun `characters outside the BMP, lone surrogates and surrogate escapes are refused`() {
        val emoji = String(Character.toChars(0x1F600))
        val lone = Char(0xD800).toString()
        assertEquals(listOf(emoji), PortableRegex.dialectViolations("^a${emoji}b$"))
        assertEquals(listOf(lone), PortableRegex.dialectViolations("^a${lone}b$"))
        assertEquals(listOf(lone), PortableRegex.dialectViolations("^[a$lone]$"))
        assertEquals(listOf("\\uDC00"), PortableRegex.dialectViolations("^\\uDC00$"))
        assertNotNull(whole("^\\uFFFD$", "\uFFFD"))
    }

    @Test
    fun `a named group inside a group that can repeat is refused, by its opener (T3-56)`() {
        for (q in listOf("*", "+", "{2}", "{0,}", "{1,2}", "*?", "{2,}?")) {
            assertEquals(listOf("(?<amount>"), PortableRegex.dialectViolations("^(?:x(?<amount>\\d))$q$"), q)
            assertEquals(listOf("(?<amount>"), PortableRegex.dialectViolations("^(?:((?:(?<amount>\\d)))y)$q$"), q)
        }
        for (q in listOf("?", "??", "{0}", "{1}", "{0,1}", "{1,1}")) {
            assertEquals(emptyList(), PortableRegex.dialectViolations("^(?:x(?<amount>\\d))$q$"), q)
        }
        // The group itself may repeat; a named group after the repeated group is fine.
        assertEquals(emptyList(), PortableRegex.dialectViolations("^(?<amount>\\d)+(?:a)*(?<kind>b)$"))
        // Its own capture is the last repetition, as in every engine.
        assertEquals("3", whole("^(?<amount>\\d)+$", "123")!!["amount"])
    }

    private fun folded(p: String) = DialectTranslator(p, foldAscii = true).translate().java

    @Test
    fun `flag i folds ASCII letters only, by a rewrite (T2-18)`() {
        assertEquals("^[kK][Kk]\\u002e$", folded("^k\\x4B\\.$"))
        assertEquals("^[a-f0-9A-F][^kK][X-cA-Cx-z]$", folded("^[a-f0-9][^k][X-c]$"))
        assertEquals("^\\u00e9[0-9]$", folded("^\\u00E9\\d$"))
        // Named group names and quantifiers are not literals.
        assertEquals("^([kK]){2}$", folded("^(?<amount>k){2}$"))
        val kelvin = "\u212A"
        assertNull(whole("^k$", kelvin, "i"))
        assertNotNull(whole("^[^k]$", kelvin, "i"))
        assertNull(whole("^ss$", "\u00DF", "i"))
        assertNull(whole("^\u00E9$", "\u00C9", "i"))
        assertNotNull(whole("^bki\\. ?ir$", "BKI. IR", "i"))
    }

    @Test
    fun `dot stops at newline unless s, i is case-insensitive`() {
        assertNull(whole("^a.b$", "a\nb"))
        assertNotNull(whole("^a.b$", "a\nb", "s"))
        assertNotNull(whole("^abc$", "ABC", "i"))
    }
}
