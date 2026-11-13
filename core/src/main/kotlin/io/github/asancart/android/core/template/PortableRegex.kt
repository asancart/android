package io.github.asancart.android.core.template

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A Parser Template pattern compiled so it behaves like the reference engine's JavaScript RegExp
 * on every JVM and Android version (REQ-DEV-041 portable subset).
 *
 * Android's `java.util.regex` is ICU, not OpenJDK, and the app runs from API 24, so the pattern is
 * rewritten before compiling:
 * - Named groups `(?<name>…)` become plain numbered groups and are read by number:
 *   `Matcher.group(String)` only exists from API 26.
 * - `\d`, `\s`, `\w`, `\b` and their negations get their JavaScript (non-Unicode-mode) meaning.
 *   ICU's are Unicode-wide (a Persian digit is `\d` there), the JDK's `\s` is ASCII only.
 * - Inside a class, `[` and `&` are literal (they nest or intersect in Java); `[]` matches
 *   nothing and `[^]` anything, as in JavaScript.
 * - A `{` that does not start a `{n}`, `{n,}` or `{n,m}` quantifier is literal, as in JavaScript.
 * - `.` excludes only `\n` (UNIX_LINES); after normalization that is JavaScript's `.`.
 * Other escapes keep their meaning; an identity escape of a letter JavaScript doesn't define
 * (`\y`) is that letter.
 */
class PortableRegex private constructor(
    val source: String,
    private val pattern: Pattern,
    /** Named group → capture group number. */
    val groups: Map<String, Int>,
) {
    /** Full match of [text], as the reference engine's anchored `exec` with index 0 and full length. */
    fun matchWhole(text: String): Match? {
        val m: Matcher = pattern.matcher(text)
        if (!m.matches()) return null
        return Match(groups.mapValues { (_, n) -> m.group(n) })
    }

    /** Named group values; null when the group did not take part. */
    class Match(val named: Map<String, String?>) {
        operator fun get(name: String): String? = named[name]
    }

    companion object {
        /** JavaScript `\s` members, written for use inside a character class. */
        private const val JS_SPACE = "\\t\\n\\x0B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
        private const val JS_WORD = "A-Za-z0-9_"
        private const val JS_DIGIT = "0-9"
        private val QUANTIFIER = Regex("""^\{\d+(,\d*)?\}""")

        /**
         * Compiles [source] with flags `i` and/or `s` (REQ-DEV-040).
         * @throws InvalidPattern when it doesn't compile; the template is INVALID_TEMPLATE.
         */
        fun compile(source: String, flags: String = ""): PortableRegex {
            val groups = namedGroupNumbers(source)
            val java = rewrite(source, groups)
            var f = Pattern.UNIX_LINES
            if ('i' in flags) f = f or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            if ('s' in flags) f = f or Pattern.DOTALL
            val compiled = try {
                Pattern.compile(java, f)
            } catch (e: PatternSyntaxException) {
                throw InvalidPattern(e.description ?: e.message ?: "invalid pattern")
            }
            return PortableRegex(source, compiled, groups)
        }

        /** Numbers capture groups as JavaScript does: every `(` that is not `(?…` except `(?<name>`. */
        internal fun namedGroupNumbers(src: String): Map<String, Int> {
            val named = LinkedHashMap<String, Int>()
            var count = 0
            var i = 0
            var inClass = false
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' -> i++
                    inClass -> if (c == ']') inClass = false
                    c == '[' -> {
                        inClass = true
                        // `[]` and `[^]`: the next `]` is not literal in JavaScript.
                    }
                    c == '(' -> {
                        if (src.startsWith("(?<", i) && i + 3 < src.length && src[i + 3] != '=' && src[i + 3] != '!') {
                            count++
                            val end = src.indexOf('>', i + 3)
                            if (end < 0) throw InvalidPattern("unterminated group name")
                            val name = src.substring(i + 3, end)
                            if (!GROUP_NAME.matches(name)) throw InvalidPattern("invalid group name $name")
                            if (named.put(name, count) != null) throw InvalidPattern("duplicate group name $name")
                        } else if (!src.startsWith("(?", i)) {
                            count++
                        }
                    }
                }
                i++
            }
            return named
        }

        private val GROUP_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

        private fun rewrite(src: String, groups: Map<String, Int>): String {
            val out = StringBuilder(src.length + 32)
            var i = 0
            var inClass = false
            while (i < src.length) {
                val c = src[i]
                if (c == '\\') {
                    if (i + 1 >= src.length) throw InvalidPattern("\\ at end of pattern")
                    val e = src[i + 1]
                    i += 2
                    when (e) {
                        'd' -> out.append(if (inClass) JS_DIGIT else "[$JS_DIGIT]")
                        'D' -> out.append("[^$JS_DIGIT]")
                        's' -> out.append(if (inClass) JS_SPACE else "[$JS_SPACE]")
                        'S' -> out.append("[^$JS_SPACE]")
                        'w' -> out.append(if (inClass) JS_WORD else "[$JS_WORD]")
                        'W' -> out.append("[^$JS_WORD]")
                        'b' -> out.append(if (inClass) "\\x08" else "(?:(?<=[$JS_WORD])(?![$JS_WORD])|(?<![$JS_WORD])(?=[$JS_WORD]))")
                        'B' -> out.append(if (inClass) "B" else "(?:(?<=[$JS_WORD])(?=[$JS_WORD])|(?<![$JS_WORD])(?![$JS_WORD]))")
                        'v' -> out.append("\\x0B")
                        '0' -> if (i < src.length && src[i].isDigit()) out.append("\\0") else out.append("\\x00")
                        'k' -> {
                            if (i < src.length && src[i] == '<') {
                                val end = src.indexOf('>', i)
                                val n = if (end > 0) groups[src.substring(i + 1, end)] else null
                                    ?: throw InvalidPattern("unknown group in \\k")
                                out.append("\\").append(n)
                                i = end + 1
                            } else {
                                out.append('k')
                            }
                        }
                        'n', 'r', 't', 'f', 'c', 'x', 'u' -> out.append('\\').append(e)
                        in '1'..'9' -> out.append('\\').append(e)
                        else -> when {
                            // JavaScript identity escape of a letter: the letter itself.
                            e.isLetter() -> out.append(e)
                            else -> out.append('\\').append(e)
                        }
                    }
                    continue
                }
                if (inClass) {
                    when (c) {
                        ']' -> {
                            inClass = false
                            out.append(']')
                        }
                        '[', '&' -> out.append('\\').append(c)
                        else -> out.append(c)
                    }
                    i++
                    continue
                }
                when (c) {
                    '[' -> {
                        when {
                            src.startsWith("[]", i) -> {
                                out.append("(?!)")
                                i += 2
                            }
                            src.startsWith("[^]", i) -> {
                                out.append("[\\s\\S]")
                                i += 3
                            }
                            src.startsWith("[^", i) -> {
                                inClass = true
                                out.append("[^")
                                i += 2
                            }
                            else -> {
                                inClass = true
                                out.append('[')
                                i++
                            }
                        }
                    }
                    '(' -> {
                        if (src.startsWith("(?<", i) && i + 3 < src.length && src[i + 3] != '=' && src[i + 3] != '!') {
                            out.append('(')
                            i = src.indexOf('>', i + 3) + 1
                        } else {
                            out.append('(')
                            i++
                        }
                    }
                    '{' -> {
                        val q = QUANTIFIER.find(src.substring(i))
                        if (q != null) {
                            out.append(q.value)
                            i += q.value.length
                        } else {
                            out.append("\\{")
                            i++
                        }
                    }
                    '}' -> {
                        out.append("\\}")
                        i++
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
            if (inClass) throw InvalidPattern("unterminated character class")
            return out.toString()
        }
    }

    class InvalidPattern(message: String) : Exception(message)
}
