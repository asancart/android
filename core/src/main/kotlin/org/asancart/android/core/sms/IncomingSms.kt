package org.asancart.android.core.sms

import java.time.Instant

/**
 * One SMS as it arrived on the Device: the raw text before normalization (REQ-APP-016).
 *
 * The text stays on the Device (DEC-003, REQ-APP-012). [toString] never prints it, so an SMS that
 * ends up in a log line or a crash report leaks nothing but its sender and time (REQ-OPS-011).
 */
class IncomingSms(
    val sender: String,
    val body: String,
    /** Android receive time; becomes the Transfer Report `sms_time` (REQ-DEV-002). */
    val receivedAt: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is IncomingSms && other.sender == sender && other.body == body && other.receivedAt == receivedAt

    override fun hashCode(): Int = 31 * (31 * sender.hashCode() + body.hashCode()) + receivedAt.hashCode()

    override fun toString(): String = "IncomingSms(sender=$sender, body=<${body.length} chars>, receivedAt=$receivedAt)"
}
