package io.github.asancart.android.core.sms

import io.github.asancart.android.core.template.NormalizeFlags

/**
 * Normalization seam (REQ-APP-016). Implemented with the template engine in T4.2.
 *
 * - [normalizeText]: REQ-DEV-050, line breaks first, then each step its flag allows; ZWNJ kept.
 * - [normalizeSender]: REQ-DEV-051, numeric senders keep their last 10 digits, text IDs lowercase.
 */
interface SmsNormalizer {
    fun normalizeText(text: String, flags: NormalizeFlags = NormalizeFlags()): String

    fun normalizeSender(sender: String): String
}
