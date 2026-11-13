package io.github.asancart.android.log

/**
 * REQ-OPS-003 log filter, applied to every message, field and stack trace before it is written or
 * sent. Logs never hold IPG Tokens, Device credentials, callback secrets, OTP or pairing codes,
 * passwords, SMS text or balances (REQ-OPS-011). Card numbers keep their first and last 4 digits
 * (`6037********5678`), phone numbers their last 4.
 *
 * Callers already log ids, names and counts only; this is the second line of defence:
 * - Persian or Arabic script (an SMS, a Shop or bank name) becomes `<text>`.
 * - Fields whose name says secret (`credential`, `token`, `password`, `otp`, `code`, `body`,
 *   `balance`, ...) lose their value; so do `Bearer …` and `key=value` / `"key":"value"` pairs.
 *   A JWT is removed wherever it stands.
 * - Card numbers, Sheba numbers (with any separator, also glued to a word) and phone numbers are
 *   masked; any other number of 6 or more digits (amounts, balances, account numbers), or of 10 or
 *   more digits written in groups, becomes `<number>`. UUIDs, dates, times and IPv4 addresses
 *   are kept.
 */
object LogRedactor {
    const val REDACTED = "<redacted>"

    private val SECRET_KEY = Regex(
        "(?i)(credential|token|secret|password|passwd|pass|otp|pin|cvv2?|code|pairing|authorization|auth|cookie|session|body|text|sms|message|balance|mande|account|iban|sheba)",
    )

    /** Values of fields whose name says they are secret are never logged. */
    fun isSecretKey(key: String): Boolean = SECRET_KEY.containsMatchIn(key) && key != "request_id"

    private val ARABIC_SCRIPT = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF][\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF\\u200C\\u200D\\s\\d.,:;!?()/+\\-*]*")
    /** A JWT (or JWE) anywhere, e.g. after `token refresh`: base64url `{"` header, then dotted parts. */
    private val JWT = Regex("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]*){1,4}")

    /** `Bearer …`, `Token …`, `credential …`: the word, then the secret. */
    private val BEARER = Regex("(?i)\\b(Bearer|Basic|Token|credential)\\s+[A-Za-z0-9._~+/=-]+")

    /** A JSON `"code"`, e.g. the pairing code in the dashboard QR payload (dashboard-api.md Devices). */
    private val JSON_CODE = Regex("(?i)(\"code\"\\s*:\\s*\")([^\"]*)(\")")

    /** Pairing code shape (dashboard-api.md): `XXXX-XXXX` from 23456789ABCDEFGHJKLMNPQRSTUVWXYZ, any case. */
    private val PAIRING_CODE = Regex("(?i)(?<![0-9A-Z_])[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}(?![0-9A-Z_])")

    /** Device credential (`dvc_…`) and any other long opaque token with letters and digits. */
    private val DEVICE_CREDENTIAL = Regex("\\bdvc_[A-Za-z0-9_-]+")
    private val OPAQUE_TOKEN = Regex("(?<![A-Za-z0-9_-])(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}(?![A-Za-z0-9_-])")
    private val KEY_VALUE = Regex(
        "(?i)(\"?)([A-Za-z_]*(?:credential|token|secret|password|passwd|otp|pin|pairing_code|pairing|authorization|cookie)[A-Za-z_]*)(\"?)(\\s*[:=]\\s*)(\"?)([^\\s\",;&}]+)",
    )
    private val UUID = Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b")

    /** What people put between digit groups: space, NBSP, narrow NBSP, ZWNJ, dot, dash. */
    private const val SEP = "[ \\u00A0\\u202F\\u200C.\\-]"

    /**
     * IR and 24 digits (22 to 24 accepted, for typos), with any run of [SEP] between them. No word
     * boundary before IR: `toIR82-…` is a Sheba too.
     */
    private val SHEBA = Regex("(?i)IR$SEP*(\\d{2}(?:$SEP*\\d){20,22})(?![\\d])")

    /** A date (Gregorian or Jalali year) kept whole, so two dates side by side are not one number. */
    private val DATE = Regex("(?<![\\d.\\-])(?:1[34]|19|20)\\d{2}-[01]\\d-[0-3]\\d(?![\\d.\\-])")

    /** Not next to another digit group: a card or phone shape inside a longer run is not one. */
    private const val ALONE_BEFORE = "(?<![\\d])(?<!\\d$SEP)"
    private const val ALONE_AFTER = "(?![\\d])(?!$SEP\\d)"
    private val CARD = Regex("$ALONE_BEFORE(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})$ALONE_AFTER")

    /** Iranian mobile numbers, also written with spaces or dashes: `0912 123 4567`, `+98 912-123-4567`. */
    private val PHONE = Regex("$ALONE_BEFORE(?:(?:\\+|00)98[ -]?|0)?9\\d{2}[ -]?\\d{3}[ -]?\\d{4}$ALONE_AFTER")

    /**
     * Digit groups joined by up to 3 [SEP] characters (`0105-1234-5678-01`). A group followed by `:`
     * starts a time and is left out. Runs of 10 or more digits become `<number>`.
     */
    private val GROUPED = Regex("(?<![\\d])\\d+(?:$SEP{1,3}\\d+)+(?![\\d:])")
    private val IPV4 = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")
    private val LONG_NUMBER = Regex("(?<![\\d])\\d[\\d,\\u066C]{4,}\\d(?![\\d])")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var s = toLatinDigits(text)
        s = ARABIC_SCRIPT.replace(s, "<text>")
        s = JWT.replace(s, REDACTED)
        s = BEARER.replace(s) { "${it.groupValues[1]} $REDACTED" }
        s = KEY_VALUE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4] + m.groupValues[5] + REDACTED }
        s = JSON_CODE.replace(s) { m -> m.groupValues[1] + REDACTED + m.groupValues[3] }

        // Keep UUIDs (Profile and report ids) out of the number rules; dates once a Sheba is masked.
        val kept = mutableListOf<String>()
        fun keep(m: MatchResult): String {
            kept += m.value
            return "\u0000${kept.size - 1}\u0000"
        }
        s = UUID.replace(s, ::keep)
        // Numbers with a known shape first: a dashed card number also fits the pairing-code shape.
        s = SHEBA.replace(s) { m -> "IR" + "*".repeat(20) + m.groupValues[1].filter(Char::isDigit).takeLast(4) }
        s = DATE.replace(s, ::keep)
        s = CARD.replace(s) { m -> m.groupValues[1] + "********" + m.groupValues[4] }
        s = PHONE.replace(s) { m -> "*******" + m.value.filter(Char::isDigit).takeLast(4) }
        s = GROUPED.replace(s) { m -> if (m.value.count(Char::isDigit) < 10 || IPV4.matches(m.value)) m.value else "<number>" }
        s = PAIRING_CODE.replace(s, REDACTED)
        s = DEVICE_CREDENTIAL.replace(s, REDACTED)
        s = OPAQUE_TOKEN.replace(s, REDACTED)
        s = LONG_NUMBER.replace(s) { m -> if (m.value.contains('*')) m.value else "<number>" }
        kept.forEachIndexed { i, u -> s = s.replace("\u0000$i\u0000", u) }
        return s
    }

    /** One field value: its key decides first. */
    fun redactField(key: String, value: Any?): String = when {
        value == null -> "null"
        isSecretKey(key) -> REDACTED
        else -> redact(value.toString())
    }

    private fun toLatinDigits(s: String): String {
        if (s.none { it in '\u06F0'..'\u06F9' || it in '\u0660'..'\u0669' }) return s
        return buildString(s.length) {
            for (c in s) {
                append(
                    when (c) {
                        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                        else -> c
                    },
                )
            }
        }
    }
}
