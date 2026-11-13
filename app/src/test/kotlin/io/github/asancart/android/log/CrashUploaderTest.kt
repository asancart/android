package io.github.asancart.android.log

import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.CrashReport
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** REQ-OPS-013, REQ-DEV-057: a crash report leaves the phone only for Profiles that opted in. */
class CrashUploaderTest {
    private val sent = mutableListOf<Pair<String, CrashReport>>()

    private fun api(id: String, ok: Boolean = true): DeviceApi = object : DeviceApi by StubDeviceApi() {
        override suspend fun crashReport(report: CrashReport): ApiResult<Unit> {
            sent += id to report
            return if (ok) ApiResult.Ok(Unit) else ApiResult.Unreachable("offline")
        }
    }

    private val crash = CrashUploader.crash("0.1.0", "14", "Pixel", "java.lang.IllegalStateException: card 6037991234565678\n\tat x.y(Z.kt:1)", Instant.parse("2026-11-12T10:00:00Z"))

    @Test
    fun nothingIsSentWithoutAnOptIn() {
        assertEquals(0, CrashUploader { id, _ -> api(id) }.upload(emptyMap(), crash))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun eachOptedInProfileGetsItOnce() {
        assertEquals(2, CrashUploader { id, _ -> api(id) }.upload(mapOf("default" to "https://api.asancart.org", "p2" to "https://pay.example.com"), crash))
        assertEquals(listOf("default", "p2"), sent.map { it.first })
    }

    @Test
    fun allFailedKeepsTheReportForLater() {
        assertNull(CrashUploader { id, _ -> api(id, ok = false) }.upload(mapOf("default" to "https://api.asancart.org"), crash))
    }

    @Test
    fun theTraceIsRedactedAndAtMost64Kilobytes() {
        assertFalse(crash.stackTrace.contains("6037991234565678"))
        assertTrue(crash.stackTrace.contains("6037********5678"))
        val big = CrashUploader.crash("0.1.0", "14", null, "x\n".repeat(100_000), Instant.EPOCH)
        assertTrue(big.stackTrace.toByteArray().size <= CrashUploader.MAX_TRACE_BYTES)
    }
}
