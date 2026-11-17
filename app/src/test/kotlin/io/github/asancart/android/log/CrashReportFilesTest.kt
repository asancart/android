package io.github.asancart.android.log

import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

/** T4.6 QA notes: crash reports are masked before ACRA stores them, and stored ones stay few and recent. */
@RunWith(RobolectricTestRunner::class)
class CrashReportFilesTest {
    private val now = Instant.parse("2026-11-17T08:00:00Z")

    @Test
    fun oldReportsAndTheOldestBeyondTheCapAreDeleted() {
        val dir = Files.createTempDirectory("acra").toFile()
        val files = (0 until 8).map { i ->
            File(dir, "r$i.stacktrace").apply {
                writeText("x")
                // r0, r1: 8 and 9 days old; r2..r7: i hours old.
                setLastModified(now.minus(if (i < 2) Duration.ofDays(8L + i) else Duration.ofHours(i.toLong())).toEpochMilli())
            }
        }
        assertEquals(3, CrashReportFiles.prune(files, now))
        assertEquals(listOf("r2", "r3", "r4", "r5", "r6"), dir.listFiles()!!.map { it.nameWithoutExtension }.sorted())
        dir.deleteRecursively()
    }

    @Test
    fun redactionMasksTheTraceBeforeItIsStored() {
        val data = CrashReportData()
        data.put(ReportField.STACK_TRACE, "java.lang.IllegalStateException: card 6037991234565678 token dvc_" + "a".repeat(43) + "\n\tat x.y(Z.kt:1)")
        val context = RuntimeEnvironment.getApplication()
        assertTrue(CrashRedaction().shouldSendReport(context, CoreConfigurationBuilder().build(), data))
        val stored = data.getString(ReportField.STACK_TRACE)!!
        assertFalse(stored, stored.contains("6037991234565678"))
        assertFalse(stored, stored.contains("a".repeat(43)))
        assertTrue(stored, stored.contains("at x.y(Z.kt:1)"))
    }
}
