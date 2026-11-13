package io.github.asancart.android.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** REQ-OPS-010: 5 rolling files of a fixed size, oldest dropped first; JSON lines; redacted. */
class FileLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun filesRollAndTheOldestGoes() {
        val log = FileLog(tmp.newFolder("logs"), env = "test", maxFiles = 5, maxBytes = 2_000, clock = { Instant.parse("2026-11-12T10:00:00Z") })
        repeat(400) { log.info("event_$it", "n" to it) }
        log.flush()
        val files = log.files()
        assertEquals(5, files.size)
        assertTrue(files.all { it.length() <= 2_000 })
        val all = files.flatMap { it.readLines() }
        assertTrue(all.last().contains("\"msg\":\"event_399\""))
        assertFalse(all.any { it.contains("\"msg\":\"event_0\"") })
    }

    @Test
    fun filesRollByBytesNotCharacters() {
        // Multi-byte characters (here the bullets of a masked card number, 3 bytes each in UTF-8).
        val log = FileLog(tmp.newFolder("bytes"), env = "test", maxFiles = 5, maxBytes = 1_000)
        repeat(100) { log.info("event", "masked" to "6037 99\u2022\u2022 \u2022\u2022\u2022\u2022 4821".repeat(4)) }
        log.flush()
        assertTrue(log.files().all { it.length() <= 1_000 })
        assertEquals(5, log.files().size)
    }

    @Test
    fun linesAreJsonAndRedacted() {
        val log = FileLog(tmp.newFolder("logs2"), env = "test", clock = { Instant.parse("2026-11-12T10:00:00Z") })
        log.warn("templates_refresh_failed", "http" to 503, "device_credential" to "dvc_secret_123")
        log.errorNow("crash", IllegalStateException("bad \u0645\u0627\u0646\u062F\u0647:198,088,329"), "thread" to "main")
        log.flush()
        val lines = log.files().flatMap { it.readLines() }
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("{\"time\":\"2026-11-12T10:00:00Z\",\"level\":\"WARN\",\"app\":\"android\",\"env\":\"test\",\"msg\":\"templates_refresh_failed\""))
        assertTrue(lines[0].contains("\"http\":\"503\""))
        assertFalse(lines[0].contains("dvc_secret_123"))
        assertFalse(lines[1].contains("198,088,329"))
        assertTrue(lines[1].contains("IllegalStateException"))
    }
}
