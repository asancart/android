package io.github.asancart.android.core.capture

import io.github.asancart.android.core.sms.IncomingSms
import java.time.Instant

/**
 * One entry of the local "unparsed" list (REQ-APP-015): an SMS from a bound sender that no template
 * read. It never leaves the Device (DEC-003); [toString] doesn't print [body].
 */
data class UnparsedSms(
    val id: String,
    val profileId: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val reason: UnparsedReason,
    /** The template that matched but couldn't produce a report, if any. */
    val templateName: String?,
    val templatesTried: List<String>,
) {
    val sms: IncomingSms get() = IncomingSms(sender, body, receivedAt)

    override fun toString(): String =
        "UnparsedSms(id=$id, profileId=$profileId, sender=$sender, body=<${body.length} chars>, receivedAt=$receivedAt, reason=$reason, templateName=$templateName)"
}
