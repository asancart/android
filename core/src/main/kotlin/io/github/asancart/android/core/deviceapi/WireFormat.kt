package io.github.asancart.android.core.deviceapi

import io.github.asancart.android.core.queue.TransferReport
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * JSON bodies of the Device API (snake_case, REQ-DEV-031). The HTTP adapter sends exactly these.
 */
object WireFormat {
    /** The REQ-DEV-002 item fields, the only data about an SMS that leaves the Device (DEC-003). */
    val REPORT_FIELDS = listOf("client_report_id", "card_keys", "amount", "sms_time", "reference")

    /** `POST /v1/device/reports` body: `{ "reports": [ … ] }`. */
    fun reportsRequest(reports: List<TransferReport>): String = buildJsonObject {
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
}
