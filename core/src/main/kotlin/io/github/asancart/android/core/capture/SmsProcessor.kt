package io.github.asancart.android.core.capture

import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.queue.TransferReport
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.core.sms.Normalizer
import io.github.asancart.android.core.template.ParserTemplate
import io.github.asancart.android.core.template.SetResult
import io.github.asancart.android.core.template.TemplateEngine
import io.github.asancart.android.core.template.TemplateStatus
import java.util.UUID

/**
 * What one SMS means for each Profile (REQ-APP-012, REQ-APP-015, REQ-DEV-056). Pure: storage and
 * Android capture live in the app.
 *
 * For every Profile that is on and was on when the SMS arrived, the Card Binding whose sender
 * matches the SMS sender decides. An SMS that matches no binding is not the app's business and is
 * not kept anywhere (DEC-003).
 */
class SmsProcessor(
    private val engine: TemplateEngine,
    private val newReportId: () -> String = { UUID.randomUUID().toString() },
) {
    fun process(sms: IncomingSms, profiles: List<Profile>, templates: List<ParserTemplate>): List<CaptureDecision> =
        profiles.filter { it.captures(sms.receivedAt) }.mapNotNull { decide(sms, it, templates) }

    /**
     * REQ-APP-015 re-read of one unparsed SMS for its Profile, on the user's request: DEPOSIT gives a
     * Transfer Report with a new `client_report_id` and the original receive time; IGNORED removes
     * it; anything else keeps it with the new reason. The switch is not checked: the SMS was
     * captured while the Profile was on.
     */
    fun reread(sms: IncomingSms, profile: Profile, templates: List<ParserTemplate>): CaptureDecision =
        decide(sms, profile, templates) ?: CaptureDecision.Unparsed(profile.id, UnparsedReason.NO_BINDING, emptyList())

    private fun decide(sms: IncomingSms, profile: Profile, templates: List<ParserTemplate>): CaptureDecision? {
        val bindings = profile.cardBindings.filter { Normalizer.senderMatches(listOf(it.sender), sms.sender) }
        if (bindings.isEmpty()) return null
        // Two bindings for one sender can't be told apart; T4.3 refuses to create them (REQ-APP-002).
        if (bindings.size > 1) return CaptureDecision.Unparsed(profile.id, UnparsedReason.AMBIGUOUS_BINDING, emptyList())
        val binding = bindings.single()

        val set = templatesFor(binding, sms.sender, templates)
        if (set.isEmpty()) return CaptureDecision.Unparsed(profile.id, UnparsedReason.NO_TEMPLATES, emptyList())

        val result: SetResult = engine.runSet(set, sms.body, sms.sender, requiredFields(profile, binding))
        val tried = result.order.map { set[it].name }
        val outcome = result.outcome
            ?: return CaptureDecision.Unparsed(profile.id, UnparsedReason.NO_TEMPLATE_MATCHED, tried)
        return when (outcome.status) {
            TemplateStatus.DEPOSIT -> {
                if (outcome.missingRequired.isNotEmpty()) {
                    CaptureDecision.Unparsed(profile.id, UnparsedReason.MISSING_REQUIRED_FIELD, tried, outcome.templateName)
                } else {
                    CaptureDecision.Report(
                        profile.id,
                        outcome.templateName,
                        TransferReport(
                            clientReportId = newReportId(),
                            cardKeys = binding.cardKeys,
                            amount = outcome.amount!!,
                            smsTime = sms.receivedAt,
                            reference = outcome.reference,
                        ),
                    )
                }
            }
            TemplateStatus.IGNORED -> CaptureDecision.Ignored(profile.id, outcome.templateName)
            TemplateStatus.BAD_AMOUNT -> CaptureDecision.Unparsed(profile.id, UnparsedReason.BAD_AMOUNT, tried, outcome.templateName)
            TemplateStatus.DIRECTION_UNKNOWN -> CaptureDecision.Unparsed(profile.id, UnparsedReason.DIRECTION_UNKNOWN, tried, outcome.templateName)
            // Never winners (REQ-DEV-053).
            TemplateStatus.SENDER_MISMATCH, TemplateStatus.INVALID_TEMPLATE, TemplateStatus.NO_MATCH ->
                CaptureDecision.Unparsed(profile.id, UnparsedReason.NO_TEMPLATE_MATCHED, tried)
        }
    }

    /**
     * REQ-DEV-053 "a sender's templates": every template whose `sender_patterns` match the SMS
     * sender (official ones in stored order, then custom ones), plus the binding's own template when
     * its senders don't list this sender (a custom template picked for the binding).
     */
    fun templatesFor(binding: CardBinding, sender: String, templates: List<ParserTemplate>): List<ParserTemplate> {
        val forSender = templates.filter { Normalizer.senderMatches(it.senderPatterns, sender) }
        val own = templates.firstOrNull { it.name == binding.templateName }
        return if (own != null && own !in forSender) forSender + own else forSender
    }

    /** DEC-011: the Bank Registry fields of the binding's bank, from Pair or Refresh Cards. */
    private fun requiredFields(profile: Profile, binding: CardBinding): List<String> {
        val connection = profile.connection ?: return emptyList()
        val bankIds = connection.cards.filter { it.cardKey in binding.cardKeys }.map { it.bankId }.toSet()
        return connection.banks.filter { it.id in bankIds }.flatMap { it.requiredFields }.distinct()
    }
}

/** One SMS's meaning for one Profile. */
sealed interface CaptureDecision {
    val profileId: String

    /** DEPOSIT: queue [report] for the Profile's target (REQ-APP-013). */
    data class Report(override val profileId: String, val templateName: String, val report: TransferReport) : CaptureDecision

    /** IGNORED: a withdrawal, OTP, warning or bank credit; dropped. */
    data class Ignored(override val profileId: String, val templateName: String) : CaptureDecision

    /** Kept in the local "unparsed" list (REQ-APP-015), never dropped silently. */
    data class Unparsed(
        override val profileId: String,
        val reason: UnparsedReason,
        /** Template names in the order they were tried. */
        val templatesTried: List<String>,
        /** The template that matched but couldn't produce a report, if any. */
        val templateName: String? = null,
    ) : CaptureDecision
}

/** Why a bound-sender SMS is in the unparsed list. */
enum class UnparsedReason {
    /** No template matched the whole text: a new or changed bank layout. */
    NO_TEMPLATE_MATCHED,

    /** Matched, but the amount is not a positive integer (REQ-DEV-054). */
    BAD_AMOUNT,

    /** Matched, but the sign or keyword is in neither list (REQ-DEV-055). */
    DIRECTION_UNKNOWN,

    /** A deposit without a field the bank requires (REQ-DEV-056, DEC-011). */
    MISSING_REQUIRED_FIELD,

    /** Two Card Bindings of the Profile share this sender. */
    AMBIGUOUS_BINDING,

    /** No template for this sender on the phone yet. */
    NO_TEMPLATES,

    /** On re-read: the Profile no longer has a binding for this sender. */
    NO_BINDING,
}
