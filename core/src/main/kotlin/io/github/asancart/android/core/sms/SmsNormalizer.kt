package io.github.asancart.android.core.sms

import io.github.asancart.android.core.template.NormalizeFlags

/**
 * Normalization (REQ-APP-016): REQ-DEV-050 for SMS text, REQ-DEV-051 for senders.
 * A port of the reference engine (asancart/frontend apps/admin/src/templates/engine.ts), step for
 * step, including its JavaScript notions of whitespace and trimming.
 */
interface SmsNormalizer {
    fun normalizeText(text: String, flags: NormalizeFlags = NormalizeFlags()): String

    fun normalizeSender(sender: String): String
}

object Normalizer : SmsNormalizer {
    /** U+200E, U+200F, U+061C, U+202A–U+202E, U+2066–U+2069, U+FEFF. */
    private fun isBidiControl(c: Char): Boolean =
        c == '\u200E' || c == '\u200F' || c == '\u061C' || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\uFEFF'

    /** JavaScript `\s`, which is also what `String.prototype.trim` removes. */
    fun isJsWhitespace(c: Char): Boolean = when (c) {
        '\t', '\n', '\u000B', '\u000C', '\r', ' ', '\u00A0', '\u1680', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true
        else -> c in '\u2000'..'\u200A'
    }

    /** JavaScript `String.prototype.trim`. */
    fun jsTrim(s: String): String {
        var start = 0
        var end = s.length
        while (start < end && isJsWhitespace(s[start])) start++
        while (end > start && isJsWhitespace(s[end - 1])) end--
        return s.substring(start, end)
    }

    /**
     * REQ-DEV-050's first steps, always applied whatever the flags (T2.15): the line breaks CR,
     * CRLF, U+0085, U+2028 and U+2029 become `\n`; the whitespace JavaScript, the JDK and ICU class
     * differently (U+000B, U+000C, U+00A0, U+1680, U+2000–U+200A, U+202F, U+205F, U+3000) becomes a
     * space; U+FEFF is removed; a character outside the Basic Multilingual Plane (a surrogate pair)
     * or a lone surrogate becomes U+FFFD (T2.16). Afterwards space, tab and `\n` are the only
     * whitespace, `\n` the only line break and every character one UTF-16 unit, so REQ-DEV-041's
     * `\s`, `.`, `\D`, `\S` and `[^…]` mean the same in every engine.
     */
    fun alwaysOn(s: String): String {
        if (s.none(::isAlwaysOnChar)) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '\r' -> {
                    out.append('\n')
                    if (i + 1 < s.length && s[i + 1] == '\n') i++
                }
                '\u0085', '\u2028', '\u2029' -> out.append('\n')
                '\uFEFF' -> Unit
                else -> when {
                    isOddSpace(c) -> out.append(' ')
                    Character.isSurrogate(c) -> {
                        out.append('\uFFFD')
                        if (Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) i++
                    }
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    /** Whitespace REQ-DEV-050 always turns into a space. */
    private fun isOddSpace(c: Char): Boolean = when (c) {
        '\u000B', '\u000C', '\u00A0', '\u1680', '\u202F', '\u205F', '\u3000' -> true
        else -> c in '\u2000'..'\u200A'
    }

    private fun isAlwaysOnChar(c: Char): Boolean =
        c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029' || c == '\uFEFF' || isOddSpace(c) || Character.isSurrogate(c)

    /** Arabic ي ى → Persian ی; Arabic ك → Persian ک. Also used on keyword lists (REQ-DEV-055). */
    fun normalizeLetters(s: String): String = map(s) {
        when (it) {
            '\u064A', '\u0649' -> '\u06CC'
            '\u0643' -> '\u06A9'
            else -> it
        }
    }

    /** Persian (U+06F0–U+06F9) and Arabic-Indic (U+0660–U+0669) digits → `0`–`9`. */
    fun normalizeDigits(s: String): String = map(s) {
        when (it) {
            in '\u06F0'..'\u06F9' -> '0' + (it - '\u06F0')
            in '\u0660'..'\u0669' -> '0' + (it - '\u0660')
            else -> it
        }
    }

    private fun removeBidi(s: String): String = if (s.none(::isBidiControl)) s else s.filterNot(::isBidiControl)

    private fun punctuation(s: String): String = map(s) {
        when (it) {
            '\u066C' -> ','
            '\u066B' -> '.'
            in '\uFF01'..'\uFF5E' -> (it.code - 0xFEE0).toChar()
            else -> it
        }
    }

    private val SPACE_RUN = Regex("[ \t]+")

    /** Per line: runs of spaces and tabs → one space, then trim. Line breaks are `\n` already. */
    private fun whitespace(s: String): String =
        s.split('\n').joinToString("\n") { line -> jsTrim(line.replace(SPACE_RUN, " ")) }

    private fun dropBlankLines(s: String): String = s.split('\n').filter { jsTrim(it).isNotEmpty() }.joinToString("\n")

    override fun normalizeText(text: String, flags: NormalizeFlags): String {
        var s = alwaysOn(text)
        if (flags.bidiControls) s = removeBidi(s)
        if (flags.arabicLetters) s = normalizeLetters(s)
        if (flags.digits) s = normalizeDigits(s)
        if (flags.punctuation) s = punctuation(s)
        if (flags.whitespace) s = whitespace(s)
        if (flags.dropBlankLines) s = dropBlankLines(s)
        return s
    }

    /**
     * REQ-DEV-051: bidi controls removed, digits to Latin, whitespace (JavaScript's `\s` set,
     * [isJsWhitespace]), `-`, `(` and `)` removed. Numeric IDs (`^\+?\d+$`) keep their last 10
     * digits (shorter kept whole); text IDs lowercase ASCII letters only (T2.23). An empty result
     * never matches ([senderMatches]).
     */
    override fun normalizeSender(sender: String): String {
        val compact = normalizeDigits(removeBidi(sender)).filterNot { isJsWhitespace(it) || it == '-' || it == '(' || it == ')' }
        val digits = compact.removePrefix("+")
        if (digits.isNotEmpty() && digits.all { it in '0'..'9' }) return if (digits.length > 10) digits.takeLast(10) else digits
        return lowercaseAscii(compact)
    }

    /**
     * `A`–`Z` to `a`–`z`, every other character as it is (REQ-DEV-051 step 3, T2.23). Not the
     * library's lowercasing: Kotlin `lowercase()` and Java `toLowerCase(Locale.ROOT)` turn a final
     * Greek capital sigma into `ς` where JavaScript gives `σ` (`aوΣ`), and the admin engine and the
     * server lowercase ASCII only.
     */
    fun lowercaseAscii(s: String): String = map(s) { if (it in 'A'..'Z') it + ('a' - 'A') else it }

    /** True when [sender] equals one of [patterns] after normalization. */
    fun senderMatches(patterns: List<String>, sender: String): Boolean {
        val n = normalizeSender(sender)
        return n.isNotEmpty() && patterns.any { normalizeSender(it) == n }
    }

    private inline fun map(s: String, f: (Char) -> Char): String {
        var changed = false
        val chars = CharArray(s.length) { i ->
            val c = s[i]
            val m = f(c)
            if (m != c) changed = true
            m
        }
        return if (changed) String(chars) else s
    }
}
