package io.github.asancart.android.core.template

import io.github.asancart.android.core.sms.IncomingSms

/**
 * Template engine seam (REQ-APP-017, REQ-DEV-052..056). Implemented in T4.2 as a port of the
 * reference engine, checked against every `parser-templates` fixture.
 */
interface TemplateEngine {
    /** REQ-DEV-053: a sender's templates by priority; the first that matches the whole text wins. */
    fun evaluate(templates: List<ParserTemplate>, sms: IncomingSms): TemplateResult
}

/** REQ-DEV-052 statuses. */
enum class TemplateStatus {
    SENDER_MISMATCH,
    INVALID_TEMPLATE,
    NO_MATCH,
    BAD_AMOUNT,
    DIRECTION_UNKNOWN,
    IGNORED,
    DEPOSIT,
}

/**
 * Outcome for one SMS. [templateName] is null when no template won: the SMS goes to the local
 * "unparsed" list (REQ-APP-015). [account] chooses the Card Binding on the Device and is never sent
 * (REQ-DEV-041); [toString] leaves it out, so amounts never appear next to account digits in a log
 * (REQ-OPS-011).
 */
data class TemplateResult(
    val status: TemplateStatus,
    val templateName: String? = null,
    val amountRials: Long? = null,
    val reference: String? = null,
    val account: String? = null,
) {
    override fun toString(): String =
        "TemplateResult(status=$status, templateName=$templateName, amountRials=$amountRials, " +
            "reference=${if (reference == null) "none" else "set"}, account=${if (account == null) "none" else "<redacted>"})"
}
