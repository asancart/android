package io.github.asancart.android.core.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** JavaScript (non-Unicode mode) meaning on the JVM and on ICU (REQ-DEV-041 portable subset). */
class PortableRegexTest {
    private fun whole(p: String, text: String, flags: String = "") = PortableRegex.compile(p, flags).matchWhole(text)

    @Test
    fun `named groups are numbered like JavaScript, skipping non-capturing groups and lookarounds`() {
        val r = PortableRegex.compile("^(?:x)(?=a)(?<a>a)(b)(?!c)(?<c>c)?$")
        assertEquals(mapOf("a" to 1, "c" to 3), r.groups)
        val m = assertNotNull(r.matchWhole("xab"))
        assertEquals("a", m["a"])
        assertNull(m["c"])
    }

    @Test
    fun `digit, space and word classes are the JavaScript ones`() {
        assertNull(whole("^\\d$", "\u06F5"))
        assertNotNull(whole("^\\d$", "5"))
        assertNotNull(whole("^a\\sb$", "a\u00A0b"))
        assertNotNull(whole("^a\\sb$", "a\u2003b"))
        assertNull(whole("^\\w$", "\u0628"))
        assertNotNull(whole("^[\\d,]+$", "1,2"))
        assertNotNull(whole("^[\\s\\S]*$", "a\nb"))
        assertNull(whole("^[^\\d]$", "7"))
    }

    @Test
    fun `word boundary is ASCII`() {
        assertNotNull(whole("^.*\\bab\\b.*$", "x ab y"))
        assertNull(whole("^.*\\bab\\b.*$", "xaby"))
    }

    @Test
    fun `literal braces, brackets and ampersands`() {
        assertNotNull(whole("^a{$", "a{"))
        assertNotNull(whole("^\\d{2,3}$", "123"))
        assertNotNull(whole("^[a[]$", "["))
        assertNotNull(whole("^[a&&b]$", "&"))
        assertNull(whole("^[]$", ""))
        assertNotNull(whole("^[^]$", "\n"))
    }

    @Test
    fun `dot stops at newline unless s, i is case-insensitive`() {
        assertNull(whole("^a.b$", "a\nb"))
        assertNotNull(whole("^a.b$", "a\nb", "s"))
        assertNotNull(whole("^abc$", "ABC", "i"))
    }

    @Test
    fun `a broken pattern is reported`() {
        assertFailsWith<PortableRegex.InvalidPattern> { PortableRegex.compile("^(?<amount>[0-9]+$") }
        assertFailsWith<PortableRegex.InvalidPattern> { PortableRegex.compile("^(?<a>x)(?<a>y)$") }
    }
}
