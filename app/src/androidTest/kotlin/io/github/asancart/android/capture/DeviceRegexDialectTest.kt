package io.github.asancart.android.capture

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.Direction
import io.github.asancart.android.core.template.NormalizeFlags
import io.github.asancart.android.core.template.ParserTemplate
import io.github.asancart.android.core.template.PortableRegex
import io.github.asancart.android.core.template.TemplateStatus
import io.github.asancart.android.core.template.fixtures.RegexDialectCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * T4.17, T4.18-T4.20, T4.24, T4.27, T4.26: REQ-DEV-041's regex dialect on the device's own
 * `java.util.regex`, which is ICU on Android. Every allowed construct (with its captures, the
 * empty-match and lookahead rules' allowed examples included), refused token and compile error of
 * the shared case file, bounds above ICU's own limit as `dialect`, and `\d` as `[0-9]` where ICU's
 * own `\d` is every Unicode digit. No private data: runs in CI.
 */
@RunWith(AndroidJUnit4::class)
class DeviceRegexDialectTest {
    @Test
    fun dialectCasesOnIcu() {
        val report = RegexDialectCases().run()
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: $report")
        assertTrue(report.toString(), report.failures.isEmpty())
        assertTrue(report.toString(), report.allowed >= 107 && report.refused >= 239 && report.regex >= 7)
    }

    @Test
    fun groupsTextThatDoesNotMatchFailsOnIcu() {
        // T4.24: the runner reports a groups text that doesn't match, also one whose groups are all null.
        val cases = """{"allowed": [{"construct": "runner", "pattern": "^(?<amount>\\d+)x$", "match": ["1x"], "noMatch": ["1y"],
            "groups": [{"text": "12x", "named": {"amount": "12"}}, {"text": "12y", "named": {"amount": "12"}}, {"text": "y", "named": {"amount": null}}]}],
            "refused": [], "regex": []}"""
        assertEquals(
            listOf(
                "allowed \"^(?<amount>\\d+)x$\": groups text \"12y\" doesn't match",
                "allowed \"^(?<amount>\\d+)x$\": on \"12y\": group amount is null, expected \"12\"",
                "allowed \"^(?<amount>\\d+)x$\": groups text \"y\" doesn't match",
            ),
            RegexDialectCases(cases).run().failures,
        )
    }

    @Test
    fun persianAndDevanagariDigitsAreNotBackslashD() {
        // Why the rewrite exists: ICU's own \d takes any Unicode digit (logged, not asserted).
        val icu = Pattern.compile("\\d").matcher("\u06F5").matches()
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: ICU \\d matches U+06F5: $icu")
        for (digit in listOf("\u06F5", "\u0665", "\u096B")) {
            assertNull(digit, PortableRegex.compile("^\\d$").matchWhole(digit))
            assertNull(digit, PortableRegex.compile("^[\\d,]+$").matchWhole(digit))
            assertNotNull(digit, PortableRegex.compile("^\\D$").matchWhole(digit))
        }
        assertNotNull(PortableRegex.compile("^\\d$").matchWhole("5"))
    }

    @Test
    fun boundAboveIcuLimitIsDialectNotACompileError() {
        // Why the bound rule exists (T2.59): ICU refuses a bound of 2^24 or more (logged, not asserted).
        val icu = try {
            Pattern.compile("^[0-9]{1,16777216}$")
            "compiles"
        } catch (e: PatternSyntaxException) {
            "refuses: ${e.description}"
        }
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: ICU {1,16777216}: $icu")
        for ((pattern, token) in listOf("^(?<amount>\\d{1,16777216})$" to "{1,16777216}", "^a{2147483648}$" to "{2147483648}")) {
            val e = assertThrows(PortableRegex.DialectViolation::class.java) { PortableRegex.compile(pattern) }
            assertEquals(listOf(token), e.tokens)
        }
        assertNotNull(PortableRegex.compile("^\\d{1,1000}$").matchWhole("1".repeat(1000)))
    }

    @Test
    fun namedGroupInsideExactlyOnceIsRefused() {
        // Why the exactly-once rule exists (T2.58): the JDK keeps a capture made inside a group
        // quantified {1} that the match backtracked past; JavaScript and ICU drop it (logged, not asserted).
        val m = Pattern.compile("^(?:(?:([0-9])){1}x|[0-9])$").matcher("5")
        val icu = if (m.matches()) "group 1 is ${m.group(1)}" else "no match"
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: ICU (?:(?:(\\d)){1}x|\\d) on 5: $icu")
        val e = assertThrows(PortableRegex.DialectViolation::class.java) { PortableRegex.compile("^(?:(?:(?<amount>\\d)){1}x|\\d)$") }
        assertEquals(listOf("(?<amount>"), e.tokens)
        assertNull(PortableRegex.compile("^(?:(?:(?<amount>\\d)){0,1}x|\\d)$").matchWhole("5")!!["amount"])
    }

    @Test
    fun flagIFoldsAsciiLettersOnly() {
        // Why flag i is a rewrite (T2.18): ICU's own folding takes the Kelvin sign for k (logged, not asserted).
        val icu = Pattern.compile("^k$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).matcher("\u212A").matches()
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: ICU case-insensitive k matches U+212A: $icu")
        assertNull(PortableRegex.compile("^k$", "i").matchWhole("\u212A"))
        assertNotNull(PortableRegex.compile("^k$", "i").matchWhole("K"))
        assertNull(PortableRegex.compile("^ss$", "i").matchWhole("\u00DF"))
        assertNotNull(PortableRegex.compile("^bki\\. ?ir$", "i").matchWhole("BKI.ir"))
    }

    @Test
    fun engineWithDigitsOffLeavesPersianDigitsUnmatched() {
        val engine = DefaultTemplateEngine()
        val t = ParserTemplate(
            name = "x-amount",
            senderPatterns = listOf("Bank X"),
            normalize = NormalizeFlags(digits = false),
            pattern = "^\\u0645\\u0628\\u0644\\u063A (?<amount>\\d+)$",
            direction = Direction.Fixed(deposit = true),
            priority = 10,
        )
        assertEquals(TemplateStatus.DEPOSIT, engine.run(t, "\u0645\u0628\u0644\u063A 500").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(t, "\u0645\u0628\u0644\u063A \u06F5\u06F0\u06F0").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(t, "\u0645\u0628\u0644\u063A \u096B\u0966\u0966").status)
        assertEquals(TemplateStatus.NO_MATCH, engine.run(t.copy(normalize = NormalizeFlags()), "\u0645\u0628\u0644\u063A \u096B\u0966\u0966").status)
        assertEquals(500L, engine.run(t.copy(normalize = NormalizeFlags()), "\u0645\u0628\u0644\u063A \u06F5\u06F0\u06F0").amount)
    }
}
