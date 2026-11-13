package io.github.asancart.android.core.deviceapi

import io.github.asancart.android.core.queue.TransferReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * In-memory [DeviceApi] with sample data. It never opens a connection: while BLK-001 holds (DEC-030)
 * this is the only implementation, used to build screens and flows against the contract.
 *
 * Behaviour follows `api/device-api.md` closely enough for the UI: one valid pairing code
 * (compared case-insensitively), the request validation rules, UNAUTHORIZED before pairing or after
 * unpairing, and REJECTED reports for unknown or mixed-bank Card Keys.
 */
class StubDeviceApi(
    private val pairingCode: String = SAMPLE_PAIRING_CODE,
    private val clock: () -> Instant = Instant::now,
) : DeviceApi {
    @Volatile
    private var paired = false

    override suspend fun pair(request: PairRequest): ApiResult<PairResponse> {
        if (request.deviceName.isBlank() || request.deviceName.length > 100) return validationFailed("device_name must be 1..100 characters")
        if (!request.pairingCode.trim().equals(pairingCode, ignoreCase = true)) {
            return ApiResult.Error(422, ErrorCode.PAIRING_INVALID, "Pairing code unknown, already used, or expired")
        }
        paired = true
        return ApiResult.Ok(
            PairResponse(
                deviceId = "dev_sample",
                deviceCredential = "stub-credential",
                shop = SAMPLE_SHOP,
                cards = SAMPLE_CARDS,
                banks = SAMPLE_BANKS,
            ),
        )
    }

    override suspend fun submitReports(reports: List<TransferReport>): ApiResult<List<ReportResult>> {
        if (!paired) return unauthorized()
        if (reports.isEmpty() || reports.size > 100) return validationFailed("reports must have 1..100 items")
        val bankByKey = SAMPLE_CARDS.associate { it.cardKey to it.bankId }
        return ApiResult.Ok(
            reports.map { report ->
                val banks = report.cardKeys.map { bankByKey[it] }
                when {
                    banks.any { it == null } -> ReportResult(report.clientReportId, ReportResult.Outcome.REJECTED, "UNKNOWN_CARD_KEY")
                    banks.toSet().size > 1 -> ReportResult(report.clientReportId, ReportResult.Outcome.REJECTED, "MIXED_BANKS")
                    else -> ReportResult(report.clientReportId, ReportResult.Outcome.ACCEPTED)
                }
            },
        )
    }

    override suspend fun heartbeat(request: HeartbeatRequest): ApiResult<Unit> {
        if (!paired) return unauthorized()
        val intervalOk = request.intervalSec == 0 || request.intervalSec in 60..86_400
        if (!intervalOk || request.queueLength < 0) return validationFailed("interval_sec or queue_length out of range")
        return ApiResult.Ok(Unit)
    }

    override suspend fun cards(): ApiResult<CardsResponse> =
        if (paired) ApiResult.Ok(CardsResponse(SAMPLE_CARDS, SAMPLE_BANKS)) else unauthorized()

    override suspend fun unpair(): ApiResult<Unit> {
        if (!paired) return unauthorized()
        paired = false
        return ApiResult.Ok(Unit)
    }

    /**
     * The official templates (asancart/documents `parser-templates`, bundled as
     * `official-parser-templates.json`), all published once: without [since] every one, with
     * [since] only those published at or after it (REQ-DEV-005).
     */
    override suspend fun parserTemplates(since: Instant?): ApiResult<ParserTemplatesResponse> {
        val items = officialTemplates.filter { since == null || !it.publishedAt.isBefore(since) }
        return ApiResult.Ok(ParserTemplatesResponse(serverTime = clock(), items = items))
    }

    private val officialTemplates: List<ParserTemplateItem> by lazy { loadOfficialTemplates() }

    override suspend fun crashReport(report: CrashReport): ApiResult<Unit> {
        if (!paired) return unauthorized()
        if (report.stackTrace.toByteArray().size > 64 * 1024) return validationFailed("stack_trace over 64 KB")
        return ApiResult.Ok(Unit)
    }

    private fun unauthorized() = ApiResult.Error(401, ErrorCode.UNAUTHORIZED, "Credential missing, unknown or revoked")

    private fun validationFailed(message: String) = ApiResult.Error(422, ErrorCode.VALIDATION_FAILED, message)

    companion object {
        const val SAMPLE_PAIRING_CODE = "K7QM-2XPA"

        private fun loadOfficialTemplates(): List<ParserTemplateItem> {
            val text = StubDeviceApi::class.java.getResourceAsStream("official-parser-templates.json")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return emptyList()
            return Json.parseToJsonElement(text).jsonObject["items"]!!.jsonArray.map { e ->
                val o = e.jsonObject
                ParserTemplateItem(
                    id = o["id"]!!.jsonPrimitive.content,
                    bankId = o["bank_id"]!!.jsonPrimitive.content,
                    name = o["name"]!!.jsonPrimitive.content,
                    version = o["version"]!!.jsonPrimitive.int,
                    status = ParserTemplateItem.Status.PUBLISHED,
                    publishedAt = Instant.parse(o["published_at"]!!.jsonPrimitive.content),
                    retiredAt = null,
                    body = o["body"].toString(),
                )
            }
        }

        val SAMPLE_SHOP = ShopRef(id = "shop_sample", name = "کافه نارنج")

        val SAMPLE_BANKS = listOf(
            Bank(id = "melli", name = "بانک ملی", requiredFields = emptyList()),
            Bank(id = "mellat", name = "بانک ملت", requiredFields = emptyList()),
        )

        val SAMPLE_CARDS = listOf(
            Card(cardId = "card_1", cardKey = "ck_melli_4821", maskedNumber = "6037 99•• •••• 4821", bankId = "melli"),
            Card(cardId = "card_2", cardKey = "ck_mellat_1907", maskedNumber = "6104 33•• •••• 1907", bankId = "mellat"),
        )
    }
}
