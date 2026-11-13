package io.github.asancart.android.capture

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.fixtures.ParserTemplateFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T4.2 check 0: every `parser-templates` fixture, cross-bank negative and reference pair, on the
 * device's own `java.util.regex` (ICU on Android) and Android's text APIs.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTemplateFixturesTest {
    @Test
    fun allFixturesPassOnTheDevice() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        // The fixtures are private (Asancart documents) and only in locally built test APKs.
        assumeTrue("fixtures not synced (scripts/sync-parser-templates.sh)", assets.list("parser-templates")?.contains("oracle.json") == true)
        val suite = ParserTemplateFixtures { name -> assets.open("parser-templates/$name").use { it.readBytes().toString(Charsets.UTF_8) } }
        val report = suite.run(DefaultTemplateEngine())
        Log.i("AsancartFixtures", "API ${Build.VERSION.SDK_INT}: $report")
        assertTrue(report.toString(), report.failures.isEmpty())
        assertEquals(84, report.fixturesTotal)
        assertEquals(report.fixturesTotal, report.fixturesPassed)
        assertEquals(report.negativesTotal, report.negativesPassed)
        assertEquals(report.oraclePairsTotal, report.oraclePairsAgreeing)
    }
}
