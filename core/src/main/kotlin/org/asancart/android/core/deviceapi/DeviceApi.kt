package org.asancart.android.core.deviceapi

import org.asancart.android.core.queue.TransferReport
import java.time.Instant

/**
 * Device API seam: the typed contract of `api/device-api.md` (base path `/v1/device`, DEC-010).
 *
 * One instance talks to one API target with one Device credential. Until the owner lifts BLK-001
 * (DEC-030) the app only has [StubDeviceApi]; the HTTP adapter is added behind this interface
 * later, so screens and the queue don't change. Field names here are Kotlin style; the adapter maps
 * them to the snake_case JSON of the contract.
 */
interface DeviceApi {
    /** REQ-DEV-001, no auth. */
    suspend fun pair(request: PairRequest): ApiResult<PairResponse>

    /** REQ-DEV-002: 1..100 reports; results in request order. */
    suspend fun submitReports(reports: List<TransferReport>): ApiResult<List<ReportResult>>

    /** REQ-DEV-003. */
    suspend fun heartbeat(request: HeartbeatRequest): ApiResult<Unit>

    /** REQ-DEV-008. */
    suspend fun cards(): ApiResult<CardsResponse>

    /** REQ-DEV-009: revokes the calling credential. */
    suspend fun unpair(): ApiResult<Unit>

    /** REQ-DEV-004, no auth. [since] is always a previous `server_time` (REQ-DEV-006). */
    suspend fun parserTemplates(since: Instant?): ApiResult<ParserTemplatesResponse>

    /** REQ-DEV-057, only when the user enabled crash reports for the Profile (REQ-OPS-013). */
    suspend fun crashReport(report: CrashReport): ApiResult<Unit>
}

/** Every call either returns a value, a contract error (REQ-DEV-031), or never reached the target. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    data class Error(val httpStatus: Int, val code: String, val message: String) : ApiResult<Nothing>

    /** Network error or no response: the queue keeps the item and retries (REQ-DEV-011). */
    data class Unreachable(val reason: String) : ApiResult<Nothing>
}

/** Request-level error codes (device-api.md "Error codes"). */
object ErrorCode {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val PAIRING_INVALID = "PAIRING_INVALID"
    const val PLAN_LIMIT = "PLAN_LIMIT"
}

data class PairRequest(val pairingCode: String, val deviceName: String, val appVersion: String)

/**
 * [deviceCredential] is shown once and is a secret: never logged (REQ-OPS-003), so [toString]
 * hides it.
 */
data class PairResponse(
    val deviceId: String,
    val deviceCredential: String,
    val shop: ShopRef,
    val cards: List<Card>,
    val banks: List<Bank>,
) {
    override fun toString(): String =
        "PairResponse(deviceId=$deviceId, deviceCredential=<redacted>, shop=$shop, cards=$cards, banks=$banks)"
}

data class ShopRef(val id: String, val name: String)

/** A Shop's VERIFIED, enabled Card. [maskedNumber] is already masked by the server. */
data class Card(val cardId: String, val cardKey: String, val maskedNumber: String, val bankId: String)

/** DEC-011: [requiredFields] lists Transfer Report fields the bank needs, e.g. `reference`. */
data class Bank(val id: String, val name: String, val requiredFields: List<String>)

data class CardsResponse(val cards: List<Card>, val banks: List<Bank>)

data class ReportResult(val clientReportId: String, val result: Outcome, val reason: String? = null) {
    /** REQ-DEV-011: every outcome removes the item from the queue. */
    enum class Outcome { ACCEPTED, DUPLICATE, REJECTED }
}

data class HeartbeatRequest(
    /** `0` turns monitoring off (REQ-DEV-022); otherwise 60..86400. */
    val intervalSec: Int,
    val appVersion: String,
    val queueLength: Int,
    val lastSmsAt: Instant?,
)

data class ParserTemplatesResponse(val serverTime: Instant, val items: List<ParserTemplateItem>)

/** [body] is schema v1 JSON, null when [status] is `retired` (REQ-DEV-006: apply by [id]). */
data class ParserTemplateItem(
    val id: String,
    val bankId: String,
    val name: String,
    val version: Int,
    val status: Status,
    val publishedAt: Instant,
    val retiredAt: Instant?,
    val body: String?,
) {
    enum class Status { PUBLISHED, RETIRED }
}

/** REQ-DEV-057: no SMS text, amounts or credentials in [stackTrace] (REQ-OPS-003). */
data class CrashReport(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String?,
    val stackTrace: String,
    val happenedAt: Instant,
)
