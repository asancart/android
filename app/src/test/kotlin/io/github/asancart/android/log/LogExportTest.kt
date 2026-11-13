package io.github.asancart.android.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** REQ-OPS-012 with Q-047's default: one .txt, the newest 1 MB, starting at a line. */
class LogExportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun oneTextFileWithTheNewestLinesUpToOneMegabyte() {
        val log = FileLog(tmp.newFolder("logs"), env = "test", maxFiles = 5, maxBytes = 1_000_000)
        repeat(12_000) { log.info("event_$it", "padding" to "x".repeat(100)) }
        val out = tmp.newFolder("exports")
        val first = LogExport.write(log, out, "0.1.0", Instant.parse("2026-11-12T10:00:00Z"))
        val second = LogExport.write(log, out, "0.1.0", Instant.parse("2026-11-12T10:00:01Z"))
        assertEquals(listOf(second.name), out.list()!!.toList())
        assertTrue(first.name.endsWith(".txt"))
        assertTrue(second.length() <= LogExport.MAX_BYTES)
        val lines = second.readLines()
        assertTrue(lines[0].startsWith("Asancart for Android 0.1.0"))
        assertTrue(lines[1].startsWith("{\"time\""))
        assertTrue(lines.last().contains("event_11999"))
    }

    @Test
    fun newestCutsAtALineStart() {
        val parts = listOf("aaaa\nbbbb\n".toByteArray(), "cccc\n".toByteArray())
        assertEquals("cccc\n", String(LogExport.newest(parts, 7)))
        assertEquals("aaaa\nbbbb\ncccc\n", String(LogExport.newest(parts, 100)))
    }
}
