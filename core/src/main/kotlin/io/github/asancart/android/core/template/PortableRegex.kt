package io.github.asancart.android.core.template

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A Parser Template pattern in REQ-DEV-041's regex dialect, compiled so it means on this platform
 * what it means in the admin engine (JavaScript `RegExp`, no `u` flag) and on the server (JDK).
 *
 * The pattern is checked against the dialect before anything is compiled: every token outside it
 * is reported in a [DialectViolation] (code `dialect`, REQ-DEV-043). Inside the dialect, the
 * pattern is rewritten for `java.util.regex`, which on Android is ICU and runs from API 24:
 * - `\d` becomes `[0-9]` (`0-9` inside a class) and `\D` `[^0-9]`: ICU's `\d` is every Unicode
 *   digit, so a Persian or Devanagari digit would match it (REQ-APP-016). `\s` becomes the
 *   dialect's ASCII class `[ \t\n\x0B\f\r]` and `\S` its complement; after REQ-DEV-050's always-on
 *   steps no whitespace is left on which the engines differ anyway.
 * - Named groups `(?<name>…)` become plain groups read by number (`Matcher.group(String)` only
 *   exists from API 26), numbered as JavaScript does.
 * - Every literal character but an ASCII letter or digit is written as a `\u` escape, so ICU's
 *   set syntax (`[:…:]`, `{…}`, `&`, ignored white space) never applies inside a class. A `-`
 *   that isn't a range is a literal, as in JavaScript.
 * - `.` excludes only `\n` (UNIX_LINES): JavaScript's `.` on normalized text.
 * - Flag `i` folds ASCII letters only (T2.18): with it, an ASCII letter outside a class becomes a
 *   class of both cases (`k` to `[kK]`) and a class gets the other case of every ASCII letter it
 *   holds (`[a-f]` to `[a-fA-F]`, `[^k]` to `[^kK]`). Nothing is compiled case-insensitive, so
 *   ICU's full case folding (ß and `ss`, the Kelvin sign and `k`) never applies.
 * The match is a full match, `Matcher.matches()`: the text matches `^(?:P)$` (T2.16).
 */
class PortableRegex private constructor(
    val source: String,
    private val pattern: Pattern,
    /** Named group → capture group number. */
    val groups: Map<String, Int>,
) {
    /** Full match of [text]: it matches `^(?:P)$` (REQ-DEV-041), which is `Matcher.matches()`. */
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
        /**
         * Compiles [source] with flags `i` and/or `s` (REQ-DEV-040): `i` folds ASCII letters only, by
         * the rewrite (T2.18); `s` is DOTALL.
         * @throws DialectViolation when a token is outside REQ-DEV-041's dialect; nothing is compiled.
         * @throws InvalidPattern when the pattern is inside the dialect but doesn't compile.
         */
        fun compile(source: String, flags: String = ""): PortableRegex {
            val t = DialectTranslator(source, foldAscii = 'i' in flags).translate()
            if (t.violations.isNotEmpty()) throw DialectViolation(t.violations)
            t.error?.let { throw InvalidPattern(it) }
            var f = Pattern.UNIX_LINES
            if ('s' in flags) f = f or Pattern.DOTALL
            val compiled = try {
                Pattern.compile(t.java, f)
            } catch (e: PatternSyntaxException) {
                throw InvalidPattern(e.description ?: e.message ?: "invalid pattern")
            }
            return PortableRegex(source, compiled, t.groups)
        }

        /** Tokens of [source] outside REQ-DEV-041's dialect, each once, in order; empty when inside. */
        fun dialectViolations(source: String): List<String> = DialectTranslator(source).translate().violations
    }

    /** The pattern doesn't compile: INVALID_TEMPLATE, schema code `regex`. */
    open class InvalidPattern(message: String) : Exception(message)

    /** [tokens] are outside REQ-DEV-041's dialect: schema code `dialect`, one error per token. */
    class DialectViolation(val tokens: List<String>) : InvalidPattern("outside the regex dialect: " + tokens.joinToString(" "))
}

/**
 * One pass over a pattern: the REQ-DEV-041 dialect check and the rewrite for `java.util.regex`.
 * Every token outside the dialect is recorded with its position and skipped, and scanning goes on,
 * so all are reported: one per distinct token, in order of first occurrence (REQ-DEV-043). The
 * first error of a pattern inside the dialect that still doesn't compile (an unclosed class, a
 * range out of order, a final `\`) is kept as [Result.error]; the JDK reports the others.
 */
internal class DialectTranslator(private val src: String, private val foldAscii: Boolean = false) {
    class Result(val java: String, val groups: Map<String, Int>, val violations: List<String>, val error: String?)

    private val out = StringBuilder(src.length + 32)
    private val found = ArrayList<Token>()
    private var error: String? = null
    private val groups = LinkedHashMap<String, Int>()
    private var groupCount = 0
    private var i = 0

    /** The last token can take a quantifier: a character, `.`, an escape, a class or a group's `)`. */
    private var quantifiable = false

    /** A refused token and where it starts. */
    private class Token(val at: Int, val text: String)

    /** An open group and the named group openers inside it, at any depth (T3.56). */
    private class Frame(val named: Token?) {
        val inner = ArrayList<Token>()
    }

    private val open = ArrayList<Frame>()

    /** Named group openers inside the group whose `)` came last, until another token comes. */
    private var justClosed: List<Token>? = null

    fun translate(): Result {
        while (i < src.length) {
            val c = src[i]
            val closing = c == ')'
            when (c) {
                '\\' -> {
                    when (val e = readEscape()) {
                        is Escape.Literal -> out.append(literalOutside(e.c))
                        is Escape.Shorthand -> out.append(e.outside)
                        is Escape.Refused -> Unit
                    }
                    quantifiable = true
                }
                '[' -> charClass()
                '(' -> group()
                ')' -> closeGroup()
                '|' -> emit("|", quantifiable = false)
                '.' -> emit(".", quantifiable = true)
                '^', '$' -> {
                    // Only as the first and the last character; after either, nothing to repeat.
                    if (i == (if (c == '^') 0 else src.length - 1)) emit(c.toString(), quantifiable = false) else refuse(c.toString())
                    quantifiable = false
                }
                '*', '+', '?' -> quantifier(1)
                '{' -> {
                    val len = braceQuantifierLength(i)
                    if (len > 0) {
                        quantifier(len)
                    } else {
                        // Up to its `}` when one closes it with no `{` between, else `{` alone.
                        var j = i + 1
                        while (j < src.length && src[j] != '{' && src[j] != '}') j++
                        refuse(if (j < src.length && src[j] == '}') src.substring(i, j + 1) else "{")
                    }
                }
                '}', ']' -> refuse(c.toString())
                else -> literal()
            }
            // Only a quantifier right after a group's `)` repeats that group.
            if (!closing) justClosed = null
        }
        val violations = found.sortedBy { it.at }.map { it.text }.distinct()
        return Result(out.toString(), groups, violations, error)
    }

    private fun emit(s: String, quantifiable: Boolean) {
        out.append(s)
        i += s.length
        this.quantifiable = quantifiable
    }

    private fun report(at: Int, token: String) {
        found += Token(at, token)
    }

    /**
     * A token outside the dialect at [i]: recorded and skipped. A refused escape, `{…}`, `}` or `]`
     * counts as something to repeat (`^\w+$` gives `\w` alone); a refused `(?` token doesn't, see
     * [group] (T2.17).
     */
    private fun refuse(token: String) {
        report(i, token)
        i += token.length
        quantifiable = true
    }

    private fun fail(message: String) {
        if (error == null) error = message
    }

    /** A literal character: one of the Basic Multilingual Plane, never a surrogate. */
    private fun literal() {
        val c = src[i]
        if (Character.isSurrogate(c)) {
            refuse(surrogateToken(i))
        } else {
            out.append(literalOutside(c))
            i++
            quantifiable = true
        }
    }

    /** A character outside the BMP (a surrogate pair) or a lone surrogate at [at]: itself. */
    private fun surrogateToken(at: Int): String =
        if (Character.isHighSurrogate(src[at]) && at + 1 < src.length && Character.isLowSurrogate(src[at + 1])) src.substring(at, at + 2) else src.substring(at, at + 1)

    /** Length of `{n}`, `{n,}` or `{n,m}` (ASCII digits) at [at], else 0. */
    private fun braceQuantifierLength(at: Int): Int {
        var j = at + 1
        while (j < src.length && src[j] in '0'..'9') j++
        if (j == at + 1) return 0
        if (j < src.length && src[j] == ',') {
            j++
            while (j < src.length && src[j] in '0'..'9') j++
        }
        return if (j < src.length && src[j] == '}') j - at + 1 else 0
    }

    /** `*` `+` `?` `{n}` `{n,}` `{n,m}` can repeat more than once: `?` and an upper bound of 0 or 1 can't. */
    private fun repeats(q: String): Boolean = when {
        q == "?" -> false
        !q.startsWith("{") -> true
        q.endsWith(",}") -> true
        else -> q.substring(q.lastIndexOfAny(charArrayOf('{', ',')) + 1, q.length - 1).trimStart('0').let { it.length > 1 || (it.isNotEmpty() && it[0] > '1') }
    }

    /** A quantifier of [len] characters at [i], optionally lazy. */
    private fun quantifier(len: Int) {
        val q = src.substring(i, i + len)
        if (!quantifiable) {
            // Nothing to repeat: after `^`, a group opener, `|` or a complete quantifier. A `?` or `+`
            // after it is a quantifier of its own, with nothing to repeat either.
            refuse(q)
            quantifiable = false
            justClosed = null
            return
        }
        // T3.56: a group that can repeat may not hold a named group (JavaScript clears its captures
        // at each repetition, the JDK and ICU keep the last one). The group's own name may repeat.
        if (repeats(q)) justClosed?.forEach { report(it.at, it.text) }
        justClosed = null
        out.append(q)
        val at = i
        i += len
        if (i < src.length && src[i] == '?') {
            out.append('?')
            i++
        } else if (i < src.length && src[i] == '+') {
            // Possessive: Java only. The token is its last character and the `+`.
            report(at, q.last() + "+")
            i++
        }
        quantifiable = false
    }

    private fun group() {
        val rest = src.substring(i, minOf(i + 4, src.length))
        when {
            rest.startsWith("(?:") || rest.startsWith("(?=") || rest.startsWith("(?!") -> {
                emit(rest.substring(0, 3), quantifiable = false)
                open += Frame(null)
            }
            rest == "(?<=" || rest == "(?<!" || rest.startsWith("(?>") -> {
                val token = if (rest.startsWith("(?>")) "(?>" else rest
                refuse(token)
                out.append('(')
                open += Frame(null)
            }
            rest.startsWith("(?<") -> namedGroup()
            rest.startsWith("(?") -> {
                // Inline flags up to their `)` or `:`; any other `(?` construct: its first 3 characters.
                var j = i + 2
                while (j < src.length && (src[j] in 'a'..'z' || src[j] in 'A'..'Z' || src[j] == '-')) j++
                val flags = j > i + 2 && j < src.length && (src[j] == ')' || src[j] == ':')
                val token = if (flags) src.substring(i, j + 1) else src.substring(i, minOf(i + 3, src.length))
                refuse(token)
                if (!token.endsWith(")")) {
                    out.append('(')
                    open += Frame(null)
                }
            }
            else -> {
                groupCount++
                emit("(", quantifiable = false)
                open += Frame(null)
            }
        }
        // Every opener, refused or not (inline flags, lookbehind, `(?>`, other `(?`), leaves nothing
        // to repeat: `^(?i)*a$` gives `(?i)` then `*` (T2.17).
        quantifiable = false
    }

    /** `(?<name>`: a name of ASCII letters and digits, a letter first, used once. */
    private fun namedGroup() {
        var j = i + 3
        while (j < src.length && src[j] != '>' && src[j] != ')') j++
        if (j >= src.length || src[j] != '>') {
            refuse("(?<")
            out.append('(')
            open += Frame(null)
            return
        }
        val opener = Token(i, src.substring(i, j + 1))
        val name = src.substring(i + 3, j)
        val wellFormed = name.isNotEmpty() && name[0].isAsciiLetter() && name.all { it.isAsciiLetter() || it in '0'..'9' }
        if (!wellFormed) {
            report(i, opener.text)
        } else {
            groupCount++
            if (groups.containsKey(name)) report(i, opener.text) else groups[name] = groupCount
        }
        out.append('(')
        i = j + 1
        open += Frame(opener)
    }

    private fun closeGroup() {
        emit(")", quantifiable = true)
        val f = open.removeLastOrNull() ?: return // an unmatched `)`: the JDK refuses it (regex)
        justClosed = f.inner.toList()
        open.lastOrNull()?.inner?.let { parent ->
            parent += f.inner
            f.named?.let { parent += it }
        }
    }

    /** A literal outside a class; with flag i an ASCII letter is a class of both cases (`k` to `[kK]`). */
    private fun literalOutside(c: Char): String {
        val o = if (foldAscii) otherCase(c) else null
        return if (o == null) escaped(c) else "[$c$o]"
    }

    /** Adds to [into] the other case of the ASCII letters in [lo]..[hi]: `a-f` adds `A-F`. */
    private fun foldRange(lo: Char, hi: Char, into: StringBuilder) {
        for ((first, last) in listOf('a' to 'z', 'A' to 'Z')) {
            val a = maxOf(lo, first)
            val b = minOf(hi, last)
            if (a <= b) into.append(otherCase(a)!!).append('-').append(otherCase(b)!!)
        }
    }

    private sealed interface Escape {
        /** One character: `\n`, `\t`, `\xhh`, a `\u` escape or an escaped metacharacter. */
        class Literal(val c: Char) : Escape

        /** `\d` `\D` `\s` `\S`: [outside] a class, or [inside] one as its content. */
        class Shorthand(val outside: String, val inside: String) : Escape

        /** Outside the dialect (recorded), or a final `\` (an error). */
        data object Refused : Escape
    }

    /** Reads the escape at [i] (a `\`) and moves past it. */
    private fun readEscape(): Escape {
        if (i + 1 >= src.length) {
            fail("\\ at end of pattern")
            i++
            return Escape.Refused
        }
        val e = src[i + 1]
        val known: Escape? = when (e) {
            'd' -> DIGIT
            'D' -> NOT_DIGIT
            's' -> SPACE
            'S' -> NOT_SPACE
            'n' -> Escape.Literal('\n')
            't' -> Escape.Literal('\t')
            in METACHARACTERS -> Escape.Literal(e)
            else -> null
        }
        if (known != null) {
            i += 2
            return known
        }
        if (e == 'x' || e == 'u') {
            val n = if (e == 'x') 2 else 4
            val h = src.substring(i + 2, minOf(i + 2 + n, src.length))
            if (h.length == n && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                val c = h.toInt(16).toChar()
                // A surrogate escape is refused whole: JavaScript counts UTF-16 units, Java code points.
                if (Character.isSurrogate(c)) {
                    refuse(src.substring(i, i + 2 + n))
                    return Escape.Refused
                }
                i += 2 + n
                return Escape.Literal(c)
            }
        }
        val close = when (e) {
            'p', 'P' -> if (src.startsWith("{", i + 2)) src.indexOf('}', i + 3) else -1
            'k' -> if (src.startsWith("<", i + 2)) src.indexOf('>', i + 3) else -1
            else -> -1
        }
        val end = when {
            close > 0 -> close + 1
            e in '0'..'9' -> {
                var j = i + 2
                while (j < src.length && src[j] in '0'..'9') j++
                j
            }
            else -> i + 1 + surrogateToken(i + 1).length
        }
        refuse(src.substring(i, end))
        return Escape.Refused
    }

    /** A class item at [at] with its source [text]: one character, `\d`-like content, or refused. */
    private class Item(val at: Int, val text: String, val c: Char?, val set: String?) {
        val refused: Boolean get() = c == null && set == null
    }

    /** The class item at [i]; a refused one is recorded. */
    private fun classItem(): Item {
        val at = i
        val c = src[i]
        val (ch, set) = when {
            c == '[' -> null.also { refuse("[") } to null
            c == '&' && src.startsWith("&&", i) -> null.also { refuse("&&") } to null
            c == '\\' -> when (val e = readEscape()) {
                is Escape.Literal -> e.c to null
                is Escape.Shorthand -> null to e.inside
                is Escape.Refused -> null to null
            }
            Character.isSurrogate(c) -> null.also { refuse(surrogateToken(i)) } to null
            else -> c.also { i++ } to null
        }
        return Item(at, src.substring(at, i), ch, set)
    }

    private fun charClass() {
        if (src.startsWith("[]", i)) return refuse("[]")
        if (src.startsWith("[^]", i)) return refuse("[^]")
        i++
        val negated = i < src.length && src[i] == '^'
        if (negated) i++
        val body = StringBuilder()
        // Flag i: the other case of each ASCII letter the class holds (T2.18).
        val other = StringBuilder()
        var closed = false
        while (i < src.length) {
            if (src[i] == ']') {
                i++
                closed = true
                break
            }
            val lo = classItem()
            // A range: an unescaped `-` between two items, not before the closing `]`.
            if (!(i + 1 < src.length && src[i] == '-' && src[i + 1] != ']')) {
                if (lo.c != null) {
                    body.append(escaped(lo.c))
                    if (foldAscii) otherCase(lo.c)?.let { other.append(escaped(it)) }
                } else if (lo.set != null) {
                    body.append(lo.set)
                }
                continue
            }
            i++
            val hi = classItem()
            when {
                // `[\d-z]`, `[a-\d]`: JavaScript reads the `-` as a literal, the JDK refuses it.
                lo.set != null || hi.set != null -> report(lo.at, lo.text + "-" + hi.text)
                lo.refused || hi.refused -> Unit
                lo.c!! > hi.c!! -> fail("range out of order in character class")
                else -> {
                    body.append(escaped(lo.c!!)).append('-').append(escaped(hi.c!!))
                    if (foldAscii) foldRange(lo.c, hi.c, other)
                }
            }
        }
        if (!closed) fail("unterminated character class")
        out.append(if (negated) "[^" else "[").append(body).append(other).append(']')
        quantifiable = true
    }

    private companion object {
        /** Escaped metacharacters of the dialect. */
        const val METACHARACTERS = ".\\/-()[]{}*+?|^$"

        /** The dialect's `\s`: space, `\t`, `\n`, U+000B, `\f`, `\r`, as class content. */
        const val ASCII_SPACE = "\\u0020\\u0009\\u000a\\u000b\\u000c\\u000d"

        val DIGIT = Escape.Shorthand("[0-9]", "0-9")
        val NOT_DIGIT = Escape.Shorthand("[^0-9]", "\\u0000-\\u002f\\u003a-\\x{10FFFF}")
        val SPACE = Escape.Shorthand("[$ASCII_SPACE]", ASCII_SPACE)
        val NOT_SPACE = Escape.Shorthand("[^$ASCII_SPACE]", "\\u0000-\\u0008\\u000e-\\u001f\\u0021-\\x{10FFFF}")

        fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        /** The other case of an ASCII letter, else null. Nothing else has a case under flag i. */
        fun otherCase(c: Char): Char? = when (c) {
            in 'a'..'z' -> c - 32
            in 'A'..'Z' -> c + 32
            else -> null
        }

        /** A literal character as `java.util.regex` source: an ASCII letter or digit as it is, else a `\u` escape. */
        fun escaped(c: Char): String =
            if (c.isAsciiLetter() || c in '0'..'9') c.toString() else "\\u" + c.code.toString(16).padStart(4, '0')
    }
}
