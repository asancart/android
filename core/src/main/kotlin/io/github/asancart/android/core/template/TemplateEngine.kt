package io.github.asancart.android.core.template

/**
 * Template engine (REQ-APP-017, REQ-DEV-052..056): a port of the reference engine
 * (asancart/frontend apps/admin/src/templates/engine.ts `runTemplate` / `runTemplateSet`), checked
 * against every `parser-templates` fixture and the reference output on every pair, on the JVM
 * and on Android.
 */
interface TemplateEngine {
    /**
     * One template on one SMS (REQ-DEV-052). [sender], when given and not blank, is checked first
     * (SENDER_MISMATCH). [requiredFields] are the bank's Bank Registry fields (DEC-011).
     */
    fun run(template: ParserTemplate, smsText: String, sender: String? = null, requiredFields: List<String> = emptyList()): TemplateResult

    /** REQ-DEV-053: by priority (ties: given order); the first template that matched wins. */
    fun runSet(templates: List<ParserTemplate>, smsText: String, sender: String? = null, requiredFields: List<String> = emptyList()): SetResult
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

enum class MoneyDirection { IN, OUT }

/**
 * Outcome of one template on one SMS. [matched] is true for every status after the pattern matched
 * the whole text (BAD_AMOUNT, DIRECTION_UNKNOWN, IGNORED, DEPOSIT).
 *
 * [account] chooses the Card Binding on the Device only and is never sent (REQ-DEV-041).
 * [normalizedText] is the SMS text. [toString] prints neither, so a result in a log line carries
 * no SMS text and no account digits next to an amount (REQ-OPS-011).
 */
data class TemplateResult(
    val templateName: String,
    val status: TemplateStatus,
    val matched: Boolean = false,
    val direction: MoneyDirection? = null,
    /** Rials (toman already × 10). */
    val amount: Long? = null,
    val account: String? = null,
    val reference: String? = null,
    val kind: String? = null,
    val channel: String? = null,
    /** DEPOSIT only: Bank Registry fields the template did not capture (REQ-DEV-056). */
    val missingRequired: List<String> = emptyList(),
    val normalizedText: String = "",
    /** INVALID_TEMPLATE: the regex error; BAD_AMOUNT: the captured raw amount. */
    val detail: String? = null,
) {
    val deposit: Boolean get() = status == TemplateStatus.DEPOSIT

    override fun toString(): String =
        "TemplateResult(templateName=$templateName, status=$status, matched=$matched, direction=$direction, amount=$amount, " +
            "account=${if (account == null) "none" else "<redacted>"}, reference=${if (reference == null) "none" else "set"}, " +
            "missingRequired=$missingRequired)"
}

/** [results] are in the input order; [order] is the order they were tried in. */
data class SetResult(val winner: Int?, val results: List<TemplateResult>, val order: List<Int>) {
    /** The winning result, or null: the SMS goes to the local "unparsed" list (REQ-APP-015). */
    val outcome: TemplateResult? get() = winner?.let { results[it] }
}
