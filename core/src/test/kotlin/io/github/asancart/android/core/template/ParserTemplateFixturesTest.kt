package io.github.asancart.android.core.template

import io.github.asancart.android.core.template.fixtures.ParserTemplateFixtures
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every `parser-templates` fixture, cross-bank negative and reference pair, on the JVM (REQ-PT-040).
 * The fixtures are private (Asancart documents): `scripts/sync-parser-templates.sh` copies them
 * locally; without them this test is skipped.
 */
class ParserTemplateFixturesTest {
    private val suite = ParserTemplateFixtures { name ->
        val url = javaClass.classLoader.getResource("parser-templates/$name") ?: error("missing fixture file $name")
        url.readText()
    }

    @Test
    fun `all fixtures pass and the engine agrees with the reference on every pair`() {
        assumeTrue("fixtures not synced (scripts/sync-parser-templates.sh)", javaClass.classLoader.getResource("parser-templates/oracle.json") != null)
        val report = suite.run(DefaultTemplateEngine())
        println(report)
        assertTrue(report.failures.isEmpty(), report.toString())
        assertEquals(15, report.banks)
        assertEquals(38, report.templates)
        assertEquals(84, report.fixturesTotal)
        assertEquals(report.fixturesTotal, report.fixturesPassed)
        assertEquals(45, report.negativesTotal)
        assertEquals(report.negativesTotal, report.negativesPassed)
        assertEquals(38 * 87, report.oraclePairsTotal)
        assertEquals(report.oraclePairsTotal, report.oraclePairsAgreeing)
    }
}
