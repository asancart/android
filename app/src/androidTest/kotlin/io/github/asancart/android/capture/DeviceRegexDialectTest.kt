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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * T4.17: REQ-DEV-041's regex dialect on the device's own `java.util.regex`, which is ICU on
 * Android. Every allowed construct, refused token and compile error of the shared case file, and
 * `\d` as `[0-9]` where ICU's own `\d` is every Unicode digit. No private data: runs in CI.
 */
@RunWith(AndroidJUnit4::class)
class DeviceRegexDialectTest {
    @Test
    fun dialectCasesOnIcu() {
        val report = RegexDialectCases().run()
        Log.i("AsancartDialect", "API ${Build.VERSION.SDK_INT}: $report")
        assertTrue(report.toString(), report.failures.isEmpty())
        assertTrue(report.toString(), report.allowed >= 51 && report.refused >= 129 && report.regex >= 7)
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
