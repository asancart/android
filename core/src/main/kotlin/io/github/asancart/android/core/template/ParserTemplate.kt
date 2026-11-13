package io.github.asancart.android.core.template

/**
 * Parser Template schema v1 (REQ-APP-005, REQ-DEV-040..042): the format shared by the server, the
 * admin panel and the app. Official templates come from REQ-DEV-004; custom ones are made on the
 * Device (DEC-014). Read from JSON with [TemplateSchema], which validates it.
 */
data class ParserTemplate(
    val schemaVersion: Int = 1,
    /** Layout key, `^[a-z0-9][a-z0-9-]*$`; versions of a layout share it. */
    val name: String,
    val senderPatterns: List<String>,
    val normalize: NormalizeFlags = NormalizeFlags(),
    /** Regex over the normalized text; anchored with `^` and `$`. */
    val pattern: String,
    /** Subset of `i`, `s`. */
    val flags: String = "",
    val direction: Direction,
    val amountUnit: AmountUnit = AmountUnit.RIAL,
    /** 0..1000, lower runs first (REQ-DEV-053). */
    val priority: Int,
) {
    /** An ignore template (`direction` fixed `out`) claims same-sender non-deposits (REQ-DEV-042). */
    val isIgnore: Boolean get() = direction == Direction.Fixed(deposit = false)
}

/** REQ-DEV-050 steps; a missing flag means `true`. */
data class NormalizeFlags(
    val bidiControls: Boolean = true,
    val arabicLetters: Boolean = true,
    val digits: Boolean = true,
    val punctuation: Boolean = true,
    val whitespace: Boolean = true,
    val dropBlankLines: Boolean = true,
)

/** REQ-DEV-042. Direction comes from the template, never from a keyword alone (REQ-APP-017). */
sealed interface Direction {
    /** Every match is a deposit (`in`) or is ignored (`out`). */
    data class Fixed(val deposit: Boolean) : Direction

    /** `sign` group: `+` deposit, `-` not, anything else unknown. */
    data object Sign : Direction

    /** `kind` group looked up in [incoming] / [outgoing]. */
    data class Keyword(val incoming: List<String>, val outgoing: List<String>) : Direction
}

enum class AmountUnit(val toRials: Long) {
    RIAL(1),
    TOMAN(10),
}
