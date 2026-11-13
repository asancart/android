package io.github.asancart.android.ui.capture

import androidx.annotation.StringRes
import io.github.asancart.android.R
import io.github.asancart.android.core.capture.UnparsedReason

/** Short label for the list, and the plain-words reason for the detail screen (design C10). */
@StringRes
fun UnparsedReason.label(): Int = when (this) {
    UnparsedReason.NO_TEMPLATE_MATCHED -> R.string.reason_short_no_match
    UnparsedReason.BAD_AMOUNT -> R.string.reason_short_bad_amount
    UnparsedReason.DIRECTION_UNKNOWN -> R.string.reason_short_direction
    UnparsedReason.MISSING_REQUIRED_FIELD -> R.string.reason_short_missing_field
    UnparsedReason.AMBIGUOUS_BINDING -> R.string.reason_short_ambiguous
    UnparsedReason.NO_TEMPLATES -> R.string.reason_short_no_templates
    UnparsedReason.NO_BINDING -> R.string.reason_short_no_binding
}

@StringRes
fun UnparsedReason.explanation(): Int = when (this) {
    UnparsedReason.NO_TEMPLATE_MATCHED -> R.string.reason_no_match
    UnparsedReason.BAD_AMOUNT -> R.string.reason_bad_amount
    UnparsedReason.DIRECTION_UNKNOWN -> R.string.reason_direction
    UnparsedReason.MISSING_REQUIRED_FIELD -> R.string.reason_missing_field
    UnparsedReason.AMBIGUOUS_BINDING -> R.string.reason_ambiguous
    UnparsedReason.NO_TEMPLATES -> R.string.reason_no_templates
    UnparsedReason.NO_BINDING -> R.string.reason_no_binding
}
