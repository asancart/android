package io.github.asancart.android.core.queue

import java.time.Instant

/**
 * One Transfer Report: the only data about an SMS that leaves the Device (REQ-APP-012, DEC-003).
 * Exactly the REQ-DEV-002 item fields; balance, account digits and SMS text have no place here.
 */
data class TransferReport(
    /** UUID made on the Device; idempotency key (REQ-DEV-010). */
    val clientReportId: String,
    /** One or more Card Keys of the same bank (REQ-APP-002). */
    val cardKeys: List<String>,
    /** Rials, > 0. */
    val amount: Long,
    /** Android receive time. */
    val smsTime: Instant,
    /** Sent when the template captured one; required when the bank lists it (DEC-011). */
    val reference: String? = null,
) {
    init {
        require(cardKeys.isNotEmpty()) { "a Transfer Report needs at least one Card Key" }
        require(amount > 0) { "amount must be positive" }
    }
}
