package io.github.asancart.android.core.deviceapi

import io.github.asancart.android.core.queue.TransferReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * JSON bodies of the Device API (snake_case, REQ-DEV-031): the requests the HTTP adapter sends and
 * the responses it reads.
 */
object WireFormat {
    /** The REQ-DEV-002 item fields, the only data about an SMS that leaves the Device (DEC-003). */
    val REPORT_FIELDS = listOf("client_report_id", "card_keys", "amount", "sms_time", "reference")

    /**
     * `POST /v1/device/reports` body: `{ "device_time": …, "reports": [ … ] }`. [deviceTime] is the
     * phone clock when the request is sent (REQ-DEV-002, REQ-MATCH-041); the app always sends it.
     */
    fun reportsRequest(reports: List<TransferReport>, deviceTime: Instant? = null): String = buildJsonObject {
        if (deviceTime != null) put("device_time", deviceTime.toString())
        put(
            "reports",
            buildJsonArray {
                for (r in reports) {
                    add(
                        buildJsonObject {
                            put("client_report_id", r.clientReportId)
                            putJsonArray("card_keys") { r.cardKeys.forEach { add(it) } }
                            put("amount", r.amount)
                            put("sms_time", r.smsTime.toString())
                            put("reference", r.reference?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                }
            },
        )
    }.toString()

    /** `POST /v1/device/pair` body (REQ-DEV-001). The code is sent as typed, trimmed; the server folds its case. */
    fun pairRequest(r: PairRequest): String = buildJsonObject {
        put("pairing_code", r.pairingCode.trim())
        put("device_name", r.deviceName)
        put("app_version", r.appVersion)
    }.toString()

    /** `POST /v1/device/heartbeat` body (REQ-DEV-003): `last_sms_at` is always there, `null` allowed. */
    fun heartbeatRequest(r: HeartbeatRequest): String = buildJsonObject {
        put("interval_sec", r.intervalSec)
        put("app_version", r.appVersion)
        put("queue_length", r.queueLength)
        put("last_sms_at", r.lastSmsAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
    }.toString()

    /** `POST /v1/device/crash-reports` body (REQ-DEV-057). */
    fun crashReportRequest(r: CrashReport): String = buildJsonObject {
        put("app_version", r.appVersion)
        put("android_version", r.androidVersion)
        put("device_model", r.deviceModel?.let(::JsonPrimitive) ?: JsonNull)
        put("stack_trace", r.stackTrace)
        put("happened_at", r.happenedAt.toString())
    }.toString()

    // Responses. Each parser throws [IllegalArgumentException] on a body that isn't the documented
    // shape; the adapter answers that as [ErrorCode.INVALID_RESPONSE]. Unknown fields are ignored.

    /** REQ-DEV-001 `201`. */
    fun pairResponse(body: String): PairResponse {
        val o = obj(Json.parseToJsonElement(body))
        return PairResponse(
            deviceId = o.string("device_id"),
            deviceCredential = o.string("device_credential"),
            shop = obj(o["shop"]).let { ShopRef(it.string("id"), it.string("name")) },
            cards = cards(o),
            banks = banks(o),
        )
    }

    /** REQ-DEV-008 `200`: `{ cards, banks }`, the shapes of the Pair response. */
    fun cardsResponse(body: String): CardsResponse {
        val o = obj(Json.parseToJsonElement(body))
        return CardsResponse(cards(o), banks(o))
    }

    /** REQ-DEV-002 `200`: `{ results }`, one per request item in its order. */
    fun reportsResponse(body: String, sent: Int): List<ReportResult> {
        val results = arr(obj(Json.parseToJsonElement(body))["results"]).map { e ->
            val o = obj(e)
            val outcome = o.string("result").let { r -> ReportResult.Outcome.entries.firstOrNull { it.name == r } }
            requireNotNull(outcome) { "unknown result" }
            ReportResult(o.string("client_report_id"), outcome, o.stringOrNull("reason"))
        }
        require(results.size == sent) { "results don't match the request" }
        return results
    }

    /** REQ-DEV-004 `200`. A retired item has no body (REQ-DEV-006). */
    fun parserTemplatesResponse(body: String): ParserTemplatesResponse {
        val o = obj(Json.parseToJsonElement(body))
        return ParserTemplatesResponse(
            serverTime = time(o.string("server_time")),
            items = arr(o["items"]).map { e ->
                val i = obj(e)
                val status = when (i.string("status")) {
                    "published" -> ParserTemplateItem.Status.PUBLISHED
                    "retired" -> ParserTemplateItem.Status.RETIRED
                    else -> throw IllegalArgumentException("unknown template status")
                }
                ParserTemplateItem(
                    id = i.string("id"),
                    bankId = i.string("bank_id"),
                    name = i.string("name"),
                    version = i.int("version"),
                    status = status,
                    publishedAt = time(i.string("published_at")),
                    retiredAt = i.stringOrNull("retired_at")?.let(::time),
                    body = (i["body"] as? JsonObject)?.toString(),
                )
            },
        )
    }

    /** A REQ-DEV-031 error body `{ "error": { code, message, details?, request_id } }`, or null. */
    fun errorBody(body: String): ErrorBody? = runCatching {
        val e = obj(obj(Json.parseToJsonElement(body))["error"])
        ErrorBody(e.string("code"), e.stringOrNull("message").orEmpty(), e.stringOrNull("request_id"))
    }.getOrNull()

    data class ErrorBody(val code: String, val message: String, val requestId: String?)

    private fun cards(o: JsonObject): List<Card> = arr(o["cards"]).map { e ->
        val c = obj(e)
        Card(c.string("card_id"), c.string("card_key"), c.string("masked_number"), c.string("bank_id"))
    }

    private fun banks(o: JsonObject): List<Bank> = arr(o["banks"]).map { e ->
        val b = obj(e)
        Bank(b.string("id"), b.string("name"), arr(b["required_fields"]).map { prim(it).content })
    }

    /** ISO-8601 with `Z` or an offset (REQ-DEV-031). */
    private fun time(s: String): Instant = try {
        OffsetDateTime.parse(s).toInstant()
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("time expected", e)
    }

    private fun obj(e: JsonElement?): JsonObject = e as? JsonObject ?: throw IllegalArgumentException("object expected")

    private fun arr(e: JsonElement?): JsonArray = e as? JsonArray ?: throw IllegalArgumentException("array expected")

    private fun prim(e: JsonElement?): JsonPrimitive =
        (e as? JsonPrimitive)?.takeIf { it.isString } ?: throw IllegalArgumentException("string expected")

    private fun JsonObject.string(key: String): String = prim(this[key]).content

    private fun JsonObject.stringOrNull(key: String): String? = when (val e = this[key]) {
        null, JsonNull -> null
        else -> prim(e).content
    }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw IllegalArgumentException("integer expected")
}
