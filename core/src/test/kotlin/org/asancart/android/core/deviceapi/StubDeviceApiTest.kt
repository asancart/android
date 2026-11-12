package org.asancart.android.core.deviceapi

import kotlinx.coroutines.test.runTest
import org.asancart.android.core.queue.TransferReport
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubDeviceApiTest {
    private val api = StubDeviceApi()
    private val pair = PairRequest(pairingCode = "k7qm-2xpa", deviceName = "Galaxy A54", appVersion = "0.1.0")

    private fun report(id: String, vararg keys: String) =
        TransferReport(clientReportId = id, cardKeys = keys.toList(), amount = 25_000_000, smsTime = Instant.parse("2026-11-12T10:00:00Z"))

    @Test
    fun `pairing code is single and case-insensitive (REQ-DEV-001)`() = runTest {
        val wrong = api.pair(pair.copy(pairingCode = "AAAA-BBBB"))
        assertEquals(ErrorCode.PAIRING_INVALID, assertIs<ApiResult.Error>(wrong).code)

        val ok = assertIs<ApiResult.Ok<PairResponse>>(api.pair(pair))
        assertEquals(StubDeviceApi.SAMPLE_CARDS, ok.value.cards)
    }

    @Test
    fun `authenticated calls need pairing, and unpair revokes (REQ-DEV-030, REQ-DEV-009)`() = runTest {
        assertEquals(401, assertIs<ApiResult.Error>(api.cards()).httpStatus)
        api.pair(pair)
        assertIs<ApiResult.Ok<CardsResponse>>(api.cards())
        assertIs<ApiResult.Ok<Unit>>(api.unpair())
        assertEquals(ErrorCode.UNAUTHORIZED, assertIs<ApiResult.Error>(api.cards()).code)
    }

    @Test
    fun `reports are judged per item (REQ-DEV-002, REQ-DEV-012)`() = runTest {
        api.pair(pair)
        val results = assertIs<ApiResult.Ok<List<ReportResult>>>(
            api.submitReports(listOf(report("r1", "ck_melli_4821"), report("r2", "ck_unknown"), report("r3", "ck_melli_4821", "ck_mellat_1907"))),
        ).value

        assertEquals(listOf("r1", "r2", "r3"), results.map { it.clientReportId })
        assertEquals(ReportResult.Outcome.ACCEPTED, results[0].result)
        assertEquals("UNKNOWN_CARD_KEY", results[1].reason)
        assertEquals("MIXED_BANKS", results[2].reason)
        assertEquals(ErrorCode.VALIDATION_FAILED, assertIs<ApiResult.Error>(api.submitReports(emptyList())).code)
    }

    @Test
    fun `heartbeat accepts 0 or 60 to 86400 (REQ-DEV-003)`() = runTest {
        api.pair(pair)
        assertIs<ApiResult.Ok<Unit>>(api.heartbeat(HeartbeatRequest(0, "0.1.0", 0, null)))
        assertIs<ApiResult.Ok<Unit>>(api.heartbeat(HeartbeatRequest(300, "0.1.0", 2, null)))
        assertIs<ApiResult.Error>(api.heartbeat(HeartbeatRequest(30, "0.1.0", 0, null)))
    }
}
