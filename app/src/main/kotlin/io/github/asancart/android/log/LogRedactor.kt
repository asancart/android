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
 * - Card numbers, Sheba numbers and phone numbers are masked; any other number of 6 or more
 *   digits (amounts, balances, account numbers) becomes `<number>`. UUIDs are kept.
 */
object LogRedactor {
    const val REDACTED = "<redacted>"

    private val SECRET_KEY = Regex(
        "(?i)(credential|token|secret|password|passwd|pass|otp|pin|cvv2?|code|pairing|authorization|auth|cookie|session|body|text|sms|message|balance|mande|account|iban|sheba)",
    )

    /** Values of fields whose name says they are secret are never logged. */
    fun isSecretKey(key: String): Boolean = SECRET_KEY.containsMatchIn(key) && key != "request_id"

    private val ARABIC_SCRIPT = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF][\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF\\u200C\\u200D\\s\\d.,:;!?()/+\\-*]*")
    private val BEARER = Regex("(?i)\\b(Bearer|Basic|Token)\\s+[A-Za-z0-9._~+/=-]+")
    private val KEY_VALUE = Regex(
        "(?i)(\"?)([A-Za-z_]*(?:credential|token|secret|password|passwd|otp|pin|pairing_code|pairing|authorization|cookie)[A-Za-z_]*)(\"?)(\\s*[:=]\\s*)(\"?)([^\\s\",;&}]+)",
    )
    private val UUID = Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b")
    private val SHEBA = Regex("(?i)\\bIR\\s?(\\d[\\d ]{21,30}\\d)")
    private val CARD = Regex("(?<![\\d])(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})[ -]?(\\d{4})(?![\\d])")
    private val PHONE = Regex("(?<![\\d])(?:\\+98|0098|0)?9\\d{9}(?![\\d])")
    private val LONG_NUMBER = Regex("(?<![\\d])\\d[\\d,\\u066C]{4,}\\d(?![\\d])")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var s = toLatinDigits(text)
        s = ARABIC_SCRIPT.replace(s, "<text>")
        s = BEARER.replace(s) { "${it.groupValues[1]} $REDACTED" }
        s = KEY_VALUE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4] + m.groupValues[5] + REDACTED }

        // Keep UUIDs (Profile and report ids) out of the number rules.
        val uuids = mutableListOf<String>()
        s = UUID.replace(s) { m ->
            uuids += m.value
            "\u0000${uuids.size - 1}\u0000"
        }
        s = SHEBA.replace(s) { m -> "IR" + "*".repeat(20) + m.groupValues[1].filter(Char::isDigit).takeLast(4) }
        s = CARD.replace(s) { m -> m.groupValues[1] + "********" + m.groupValues[4] }
        s = PHONE.replace(s) { m -> "*******" + m.value.takeLast(4) }
        s = LONG_NUMBER.replace(s) { m -> if (m.value.contains('*')) m.value else "<number>" }
        uuids.forEachIndexed { i, u -> s = s.replace("\u0000$i\u0000", u) }
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
