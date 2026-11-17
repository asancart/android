package io.github.asancart.android.core.template

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A Parser Template pattern in REQ-DEV-041's regex dialect, compiled so it means on this platform
 * what it means in the admin engine (JavaScript `RegExp`, no `u` flag) and on the server (JDK).
 *
 * The pattern is checked against the dialect before anything is compiled: every token outside it
 * is reported in a [DialectViolation] (code `dialect`, REQ-DEV-043). Besides the constructs the
 * engines refuse or read differently, the check keeps a stack of open groups to refuse a named
 * group inside a group that can repeat (T3.56), inside a group quantified exactly once, `{1}` or
 * `{1,1}` (T2.58), or inside a lookahead (T2.23), and a quantifier
 * other than `{1}`, `{1,1}`, `{0}` or `{0,0}` on a group that can match the empty string (T2.32,
 * which replaces T2.19's empty-iteration rule; bounds read as numbers, T2.34). A quantifier bound
 * above 1000 is refused on anything (T2.59), read without overflow, so no library's own limit is
 * reached (ICU's is 2^24). A group that can repeat must be fixed or delimited (the loop rule,
 * T2.60, T2.66), so no match backtracks through exponentially many splits of a text.
 *
 * Inside the dialect, the pattern is rewritten for `java.util.regex`, which on Android is ICU and
 * runs from API 24:
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
 * - A quantified lookahead is wrapped in a non-capturing group, `(?=\d){1}` to `(?:(?=\d)){1}`:
 *   ICU refuses a quantifier right after a lookahead, JavaScript and the JDK take it, and the
 *   dialect allows `{1}`, `{1,1}`, `{0}` and `{0,0}` there (T2.32).
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

    /**
     * An item for the loop rule (T2.60). A character item (a character, `.`, an escape or a class)
     * has [matches]: whether it matches a character with flag `s`, with flag `i` or without it. A
     * literal character also has [literal], the character it stands for. A group has neither.
     */
    private class LoopItem(val literal: String?, val matches: ((String) -> Boolean)?) {
        var quantified = false
    }

    /**
     * An open group (T3.56, T2.23, T2.32, T2.60): its own named opener, the named group openers
     * inside it at any depth, whether it is a lookahead, and what REQ-DEV-041 needs to know whether
     * it can match the empty string: whether it is zero-width (a lookahead or lookbehind), whether a
     * finished alternative can, and, for each item of the current alternative, whether it can. For
     * the loop rule, at any depth inside it: whether it holds a `|`, how many variable quantifiers
     * and which character items; and its own items in order with whether each is quantified.
     * A lookahead also keeps where its opener starts in the rewrite ([outStart]).
     */
    private class Frame(val named: Token?, val lookahead: Boolean = false, val zeroWidth: Boolean = false, val outStart: Int = -1) {
        val inner = ArrayList<Token>()
        val items = ArrayList<Boolean>()
        var alternativeCanBeEmpty = false
        var bar = false
        var variables = 0
        val chars = ArrayList<LoopItem>()
        val sequence = ArrayList<LoopItem>()

        /** `|`: the current alternative is finished. */
        fun alternative() {
            if (items.all { it }) alternativeCanBeEmpty = true
            items.clear()
            bar = true
        }

        /**
         * The loop rule (T2.60): a group that can repeat must be fixed (no `|` and no variable
         * quantifier inside it, at any depth) or delimited (no `|`, exactly one variable quantifier,
         * and as its first or last item an unquantified literal character that no other character
         * item inside the group matches). Every iteration then starts or ends at that character,
         * which appears nowhere else in it, so a text splits into iterations one way only.
         */
        fun fixedOrDelimited(): Boolean = when {
            bar -> false
            variables == 0 -> true
            variables > 1 -> false
            else -> listOfNotNull(sequence.firstOrNull(), sequence.lastOrNull()).any { d ->
                val c = d.literal
                c != null && !d.quantified && chars.all { it === d || !it.matches!!(c) }
            }
        }

        /**
         * REQ-DEV-041 "can match the empty string" (T2.32): a lookahead or lookbehind can; any other
         * group can when any alternative can; an alternative when every item in it can, so an empty
         * alternative can.
         */
        fun canBeEmpty(): Boolean = zeroWidth || alternativeCanBeEmpty || items.all { it }
    }

    /** Open groups, innermost last. */
    private val open = ArrayList<Frame>()

    /** The pattern outside every group: only its items are kept, since nothing quantifies it. */
    private val root = Frame(null)

    /** The innermost open group, or the pattern outside every group. */
    private fun current(): Frame = open.lastOrNull() ?: root

    /**
     * An item of the current alternative that can't match the empty string: a character, `.`, an
     * escape or a class, and a refused escape, `{…}`, `}` or `]`, which count as characters (T2.32).
     * Anchors and inline flags that close at once, `(?i)`, are zero-width and add no item.
     */
    private fun item() {
        current().items += false
    }

    /**
     * A character item: an [item], and for the loop rule (T2.60) one of the group's own items and
     * one of its character items. [literal] is the character a literal character stands for.
     */
    private fun charItem(literal: String?, matches: (String) -> Boolean) {
        item()
        val f = current()
        val loop = LoopItem(literal, matches)
        f.sequence += loop
        f.chars += loop
    }

    /** A literal character: [c], or with flag i its other case (T2.60 reads no flags). */
    private fun literalItem(c: String) = charItem(c) { it == c || it == otherCase(c) }

    /** A character item that matches no character: a refused escape, `{…}`, `}` or `]` (T2.60). */
    private fun nothingItem() = charItem(null) { false }

    fun translate(): Result {
        while (i < src.length) {
            val c = src[i]
            when (c) {
                '\\' -> {
                    when (val e = readEscape()) {
                        is Escape.Literal -> {
                            out.append(literalOutside(e.c))
                            literalItem(e.c.toString())
                        }
                        is Escape.Shorthand -> {
                            out.append(e.outside)
                            charItem(null) { shorthandMatches(e.letter, it) }
                        }
                        is Escape.Refused -> nothingItem()
                    }
                    quantifiable = true
                }
                '[' -> charItem(null, charClass())
                '(' -> group()
                ')' -> closeGroup()
                '|' -> {
                    current().alternative()
                    emit("|", quantifiable = false)
                }
                '.' -> {
                    emit(".", quantifiable = true)
                    charItem(null) { true }
                }
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
                        nothingItem()
                    }
                }
                '}', ']' -> {
                    refuse(c.toString())
                    nothingItem()
                }
                else -> literal()
            }
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
            val token = surrogateToken(i)
            refuse(token)
            literalItem(token)
        } else {
            out.append(literalOutside(c))
            i++
            quantifiable = true
            literalItem(c.toString())
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

    /**
     * Exactly once or exactly never, the only quantifiers a group that can match the empty string
     * takes (T2.32): `{1}`, `{1,1}`, `{0}`, `{0,0}`. Bounds are read as numbers: `{01}` is `{1}` and
     * `{0,00}` is `{0,0}` (T2.34).
     */
    private fun onceOrNever(q: String): Boolean {
        if (!q.startsWith("{")) return false
        val bounds = q.substring(1, q.length - 1)
        val comma = bounds.indexOf(',')
        val min = (if (comma < 0) bounds else bounds.substring(0, comma)).trimStart('0')
        if (comma >= 0) {
            val max = bounds.substring(comma + 1)
            if (max.isEmpty() || max.trimStart('0') != min) return false
        }
        return min.isEmpty() || min == "1"
    }

    /**
     * A variable quantifier (T2.60): its bounds differ. `?` `*` `+` `{n,}`, and `{n,m}` with n < m by
     * numeric value (`{2,02}` is not one). `{n,m}` with n > m is not one either (T2.66): it doesn't
     * compile (code `regex`), or is `dialect` by T2.59 above 1000.
     */
    private fun variable(q: String): Boolean {
        if (!q.startsWith("{")) return true
        val bounds = q.substring(1, q.length - 1).split(',')
        if (bounds.size == 1) return false
        if (bounds[1].isEmpty()) return true
        val (n, m) = bounds.map { it.trimStart('0') }
        return n.length < m.length || (n.length == m.length && n < m)
    }

    /** Exactly once (T2.58): `{1}` or `{1,1}`, bounds read as numbers (`{01}`, `{001,1}`, `{1,01}`). */
    private fun exactlyOnce(q: String): Boolean {
        if (!q.startsWith("{")) return false
        val bounds = q.substring(1, q.length - 1).split(',')
        return bounds.all { it.isNotEmpty() && it.trimStart('0') == "1" }
    }

    /** The quantifier's minimum is 0: `?` `*` `{0}` `{0,}` `{0,m}`, bounds read as numbers. */
    private fun minimumZero(q: String): Boolean =
        if (q.startsWith("{")) q.substring(1).takeWhile { it in '0'..'9' }.all { it == '0' } else q != "+"

    /**
     * `{n}`, `{n,}` or `{n,m}` with n or m above 1000 (T2.59), n > m included. Each bound is read as a
     * number without overflow: leading zeros dropped, more than four digits is above 1000, so
     * `{00000000000000000001000}` is 1000. ICU refuses a bound of 2^24 or more and the JDK one above
     * 2^31 - 1, while JavaScript takes any; the check runs before any library sees one.
     */
    private fun aboveMaxBound(q: String): Boolean =
        q.startsWith("{") && q.substring(1, q.length - 1).split(',').any { b ->
            val digits = b.trimStart('0')
            digits.length > 4 || (digits.isNotEmpty() && digits.toInt() > MAX_BOUND)
        }

    /** The quantifier at [at] (without a lazy or possessive mark), or null. */
    private fun quantifierAt(at: Int): String? = when (src.getOrNull(at)) {
        '*', '+', '?' -> src.substring(at, at + 1)
        '{' -> braceQuantifierLength(at).takeIf { it > 0 }?.let { src.substring(at, at + it) }
        else -> null
    }

    /** A quantifier of [len] characters at [i], optionally lazy. */
    private fun quantifier(len: Int) {
        val q = src.substring(i, i + len)
        if (!quantifiable) {
            // Nothing to repeat: after `^`, a group opener, `|` or a complete quantifier. A `?` or `+`
            // after it is a quantifier of its own, with nothing to repeat either.
            refuse(q)
            quantifiable = false
            return
        }
        // T2.32: a quantified item can match the empty string when its minimum is 0, else when the
        // item can. (The group rules, T3.56 and T2.32, are checked at the group's `)`.)
        val items = current().items
        if (items.isNotEmpty()) items[items.lastIndex] = minimumZero(q) || items.last()
        // T2.60: the item it repeats is quantified; a variable one counts in the current group (a
        // group's own quantifier counts in the group around it).
        current().sequence.lastOrNull()?.quantified = true
        if (variable(q)) current().variables++
        out.append(q)
        val at = i
        i += len
        // T2.59: a bound above 1000, on anything, n > m included. The quantifier as written, a lazy `?`
        // included, in its own place; a possessive one is refused as possessive only (below).
        if (aboveMaxBound(q) && src.getOrNull(i) != '+') report(at, if (src.getOrNull(i) == '?') "$q?" else q)
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
            rest.startsWith("(?:") -> {
                emit("(?:", quantifiable = false)
                open += Frame(null)
            }
            rest.startsWith("(?=") || rest.startsWith("(?!") -> {
                val at = out.length
                emit(rest.substring(0, 3), quantifiable = false)
                open += Frame(null, lookahead = true, zeroWidth = true, outStart = at)
            }
            rest == "(?<=" || rest == "(?<!" || rest.startsWith("(?>") -> {
                val token = if (rest.startsWith("(?>")) "(?>" else rest
                refuse(token)
                out.append('(')
                // A lookbehind is zero-width (T2.32); an atomic group is not.
                open += Frame(null, zeroWidth = token != "(?>")
            }
            rest.startsWith("(?<") -> namedGroup()
            rest.startsWith("(?") -> {
                // Inline flags up to their `)` or `:`; any other `(?` construct: its first 3 characters.
                var j = i + 2
                while (j < src.length && (src[j] in 'a'..'z' || src[j] in 'A'..'Z' || src[j] == '-')) j++
                val flags = j > i + 2 && j < src.length && (src[j] == ')' || src[j] == ':')
                val token = if (flags) src.substring(i, j + 1) else src.substring(i, minOf(i + 3, src.length))
                refuse(token)
                // Inline flags that close at once, `(?i)`, open no group and add no item (zero-width).
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

    /**
     * `(?<name>`: a name of ASCII letters and digits, a letter first, used once, never inside a
     * lookahead (T2.23). The opener runs to the next `>`, else it is `(?<` (as the admin engine).
     */
    private fun namedGroup() {
        val j = src.indexOf('>', i + 3)
        if (j < 0) {
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
            // T2.23: when the match backtracks past a lookahead, the JDK keeps a capture made inside
            // it and JavaScript drops it. Found at the opener; an opener several rules refuse is one token.
            if (open.any { it.lookahead }) report(i, opener.text)
        }
        out.append('(')
        i = j + 1
        open += Frame(opener)
    }

    /** A group's `)`: the group rules look at the quantifier right after it, if any. */
    private fun closeGroup() {
        emit(")", quantifiable = true)
        val f = open.removeLastOrNull()
        if (f == null) {
            item() // an unmatched `)`: the JDK refuses it (regex)
            return
        }
        val q = quantifierAt(i)
        val canBeEmpty = f.canBeEmpty()
        // T3.56: a group that can repeat may not hold a named group (JavaScript clears its captures
        // at each repetition, the JDK and ICU keep the last one). T2.58: neither may a group quantified
        // exactly once, {1} or {1,1} (lazy or not): when the match backtracks past it, the JDK keeps a
        // capture made inside it, JavaScript and ICU drop it. The group's own name may repeat or take
        // {1}. Found here, each in its opener's place (T2.17).
        if (q != null && (repeats(q) || exactlyOnce(q))) f.inner.forEach { report(it.at, it.text) }
        // T2.32: a group that can match the empty string takes only {1}, {1,1}, {0} or {0,0}, lazy
        // or not: JavaScript drops an iteration that matched nothing, the JDK keeps it, which moves
        // captures. The quantifier as written, a lazy `?` included, in its own place; a possessive
        // one is refused as possessive only ([quantifier]).
        if (q != null && canBeEmpty && !onceOrNever(q)) {
            val after = src.getOrNull(i + q.length)
            if (after != '+') report(i, if (after == '?') "$q?" else q)
        }
        // T2.60: a group that can repeat must be fixed or delimited, else a text that fails to match
        // can split into iterations in exponentially many ways (all three engines backtrack). The
        // quantifier as written, a lazy `?` included, in its own place; a possessive one is refused
        // as possessive only. The empty-match rule may refuse the same quantifier: one token.
        if (q != null && repeats(q) && !f.fixedOrDelimited()) {
            val after = src.getOrNull(i + q.length)
            if (after != '+') report(i, if (after == '?') "$q?" else q)
        }
        // ICU refuses a quantifier on a lookahead (`(?=\d){1}`: "Syntax error in regexp pattern"),
        // which JavaScript and the JDK take, and the dialect allows {1}, {1,1}, {0} and {0,0} on one.
        // A non-capturing group around it means the same and takes the quantifier: `(?:(?=\d)){1}`.
        if (q != null && f.lookahead) {
            out.insert(f.outStart, "(?:")
            out.append(')')
        }
        current().items += canBeEmpty
        // T2.60: what the group holds is inside the group around it; the group is one of its items.
        current().let { parent ->
            parent.bar = parent.bar || f.bar
            parent.variables += f.variables
            parent.chars += f.chars
            parent.sequence += LoopItem(null, null)
        }
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
        class Shorthand(val outside: String, val inside: String, val letter: Char) : Escape

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

    /**
     * A class at [i], written for `java.util.regex`; returns what it matches for the loop rule
     * (T2.60): a character, with flag `s`, with flag `i` or without it.
     */
    private fun charClass(): (String) -> Boolean {
        if (src.startsWith("[]", i)) {
            refuse("[]")
            return { false }
        }
        if (src.startsWith("[^]", i)) {
            refuse("[^]")
            return { true }
        }
        i++
        val negated = i < src.length && src[i] == '^'
        if (negated) i++
        val body = StringBuilder()
        // Flag i: the other case of each ASCII letter the class holds (T2.18).
        val other = StringBuilder()
        val parts = ArrayList<ClassPart>()
        var closed = false
        while (i < src.length) {
            if (src[i] == ']') {
                i++
                closed = true
                break
            }
            val lo = classItem()
            parts.addPartsOf(lo)
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
            parts += ClassPart("-", null, escaped = false)
            val hi = classItem()
            parts.addPartsOf(hi)
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
        return classMatcher(parts, negated)
    }

    /**
     * A class's content as the loop rule reads it (T2.60): a [literal] character, escaped or not, or
     * a shorthand's letter ([shorthand], `\d` `\D` `\s` `\S`); neither for a refused escape, which
     * matches no character. A raw character the dialect refuses in a class (`[`, `&&`, a surrogate)
     * is read as JavaScript reads it, unit by unit.
     */
    private class ClassPart(val literal: String?, val shorthand: Char?, val escaped: Boolean)

    private fun MutableList<ClassPart>.addPartsOf(item: Item) {
        when {
            item.c != null -> add(ClassPart(item.c.toString(), null, escaped = item.text.startsWith("\\")))
            item.set != null -> add(ClassPart(null, item.text[1], escaped = true))
            item.text.startsWith("\\") -> add(ClassPart(null, null, escaped = true))
            else -> item.text.forEach { add(ClassPart(it.toString(), null, escaped = false)) }
        }
    }

    /**
     * What a class matches for the loop rule (T2.60), with flag `i` or without it: a range is two
     * literals around an unescaped `-` (an escaped `\-` is a literal `-`, T2.66); a negated class
     * matches c when c is outside it without `i`, which `[^k]` does for `K` but never for `k`.
     */
    private fun classMatcher(parts: List<ClassPart>, negated: Boolean): (String) -> Boolean {
        val singles = ArrayList<String>()
        val ranges = ArrayList<Pair<String, String>>()
        val shorthands = ArrayList<Char>()
        var k = 0
        while (k < parts.size) {
            val a = parts[k]
            val dash = parts.getOrNull(k + 1)
            val b = parts.getOrNull(k + 2)
            if (a.literal != null && dash != null && dash.literal == "-" && !dash.escaped && b?.literal != null) {
                ranges += a.literal to b.literal
                k += 3
                continue
            }
            if (a.shorthand != null) shorthands += a.shorthand else if (a.literal != null) singles += a.literal
            k++
        }
        fun has(x: String) = x in singles || ranges.any { (lo, hi) -> x >= lo && x <= hi } || shorthands.any { shorthandMatches(it, x) }
        return if (negated) { x -> !has(x) } else { x -> has(x) || has(otherCase(x)) }
    }

    private companion object {
        /** The largest quantifier bound the dialect takes (T2.59). */
        const val MAX_BOUND = 1000

        /** Escaped metacharacters of the dialect. */
        const val METACHARACTERS = ".\\/-()[]{}*+?|^$"

        /** The dialect's `\s`: space, `\t`, `\n`, U+000B, `\f`, `\r`, as class content. */
        const val ASCII_SPACE = "\\u0020\\u0009\\u000a\\u000b\\u000c\\u000d"

        val DIGIT = Escape.Shorthand("[0-9]", "0-9", 'd')
        val NOT_DIGIT = Escape.Shorthand("[^0-9]", "\\u0000-\\u002f\\u003a-\\x{10FFFF}", 'D')
        val SPACE = Escape.Shorthand("[$ASCII_SPACE]", ASCII_SPACE, 's')
        val NOT_SPACE = Escape.Shorthand("[^$ASCII_SPACE]", "\\u0000-\\u0008\\u000e-\\u001f\\u0021-\\x{10FFFF}", 'S')

        /** The dialect's `\s`: ASCII whitespace. */
        const val ASCII_SPACE_CHARS = " \t\n\u000B\u000C\r"

        /** What `\d` `\D` `\s` `\S` match ([letter]), for the loop rule (T2.60): a single character. */
        fun shorthandMatches(letter: Char, x: String): Boolean = when (letter) {
            'd' -> x.length == 1 && x[0] in '0'..'9'
            'D' -> !shorthandMatches('d', x)
            's' -> x.length == 1 && x[0] in ASCII_SPACE_CHARS
            else -> !shorthandMatches('s', x)
        }

        /** The other case of a one-character ASCII letter, else [s] itself (T2.60). */
        fun otherCase(s: String): String = if (s.length == 1) otherCase(s[0])?.toString() ?: s else s

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
