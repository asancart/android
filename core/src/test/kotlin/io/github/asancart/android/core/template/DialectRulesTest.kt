package io.github.asancart.android.core.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * REQ-DEV-041's empty-match rule (T2.32, which replaces T2.19's empty-iteration rule; bounds read as
 * numbers, T2.34), lookahead rule (T2.23), bound rule (T2.59) and exactly-once rule (T2.58), as
 * device-api.md states them. The doc's examples first, then the admin engine's and the server
 * port's own cases (frontend `dialectRules.test.ts`, backend `EmptyMatchRuleTest`,
 * `LookaheadRuleTest`, `BoundRuleTest`). The shared `regex-dialect-cases.json` holds them too, for
 * every engine, on the JVM and on ICU.
 */
class DialectRulesTest {
    private val amount = "(?<amount>"

    /** Outside the dialect with exactly these tokens, in order; nothing is compiled. */
    private fun refused(pattern: String, vararg tokens: String) {
        assertEquals(tokens.toList(), PortableRegex.dialectViolations(pattern), pattern)
        val e = assertFailsWith<PortableRegex.DialectViolation>(pattern) { PortableRegex.compile(pattern) }
        assertEquals(tokens.toList(), e.tokens, pattern)
    }

    /** Inside the dialect, and on [text] the named groups are [named] (null: took no part), as in JavaScript. */
    private fun allowed(pattern: String, text: String, vararg named: Pair<String, String?>) {
        assertEquals(emptyList(), PortableRegex.dialectViolations(pattern), pattern)
        val m = assertNotNull(PortableRegex.compile(pattern).matchWhole(text), "$pattern on $text")
        for ((name, want) in named) assertEquals(want, m[name], "$pattern on $text: $name")
    }

    private fun inside(pattern: String) = assertEquals(emptyList(), PortableRegex.dialectViolations(pattern), pattern)

    @Test
    fun `empty-match rule - the doc's refused examples, the quantifier as token (T2-32)`() {
        // A named group that matched nothing.
        refused("^(?<amount>\\d?)+$", "+")
        refused("^(?<amount>\\d*)*$", "*")
        refused("^(?:(?<amount>\\d*))?$", "?")
        // An exact {n}, n >= 2.
        refused("^(?<account>|[0-9,]){2}.$", "{2}")
        // An empty alternative first.
        refused("^(?:|(?<amount>\\d+))?\\d*x$", "?")
        // An unnamed group, before a named one.
        refused("^(?:\\d??){1,3}(?<amount>\\d+)$", "{1,3}")
        // Refused now, though T2.19 allowed it.
        refused("^(?:(?<amount>\\d+)|)?x$", "?")
        // T2.19's other examples: the quantifier is the token now, not the named group's opener.
        refused("^(?:(?<amount>\\d*)y?)?x$", "?")
        refused("^(?<amount>\\d?)??$", "??")
        // A quantified lookahead can match the empty string; the named group in it is refused too (T2.23).
        refused("^(?:(?=(?<amount>\\d+)))?\\d+$", amount, "?")
        refused("^(?=(?<amount>\\d+))?\\d+$", amount, "?")
    }

    @Test
    fun `empty-match rule - the doc's allowed examples capture as JavaScript does`() {
        allowed("^(?<amount>\\d)+$", "12", "amount" to "2")
        allowed("^(?<amount>[\\d,]+)?x$", "x", "amount" to null)
        allowed("^(?<amount>[\\d,]+)?x$", "1,2x", "amount" to "1,2")
        allowed("^(?:ref:(?<reference>\\d*))?x$", "x", "reference" to null)
        allowed("^(?:ref:(?<reference>\\d*))?x$", "ref:x", "reference" to "")
        allowed("^(?:ref:(?<reference>\\d*))?x$", "ref:7x", "reference" to "7")
        // ^(?:(?<amount>\d*)){1}$, allowed here before, is refused by T2.58 (a named group inside {1}).
        refused("^(?:(?<amount>\\d*)){1}$", amount)
        allowed("^(?<amount>\\d*){1}$", "", "amount" to "")
        // The doc's rewrite of the newly refused ^(?:(?<amount>\d+)|)?x$.
        allowed("^(?<amount>\\d+)?x$", "x", "amount" to null)
        allowed("^(?<amount>\\d+)?x$", "12x", "amount" to "12")
    }

    @Test
    fun `empty-match rule - only {1}, {1,1}, {0}, {0,0}, lazy or not, any other as written in its place`() {
        for (q in listOf("?", "*", "+", "{2}", "{3}", "{0,}", "{1,}", "{2,}", "{0,1}", "{0,2}", "{1,3}", "{2,2}", "??", "*?", "+?", "{2}?", "{1,3}?", "{0,1}?", "{1,}?")) {
            refused("^(?:a*)${q}x$", q)
            refused("^x(?:a|)$q\\w$", q, "\\w")
        }
        for (q in listOf("{1}", "{1,1}", "{0}", "{0,0}", "{1}?", "{1,1}?", "{0}?", "{0,0}?")) inside("^(?:a*)${q}x$")
        allowed("^(?<amount>\\d*){0,0}x$", "x", "amount" to null)
        allowed("^(?<amount>\\d*){1,1}?$", "12", "amount" to "12")
        refused("^(?:(?<amount>\\d*)){1,1}?$", amount) // T2.58
        // A possessive quantifier is refused as possessive only.
        refused("^(?:a?)?+x$", "?+")
        refused("^(?:a?)*+x$", "*+")
        refused("^(?:a?){2}+x$", "}+")
        // Tokens by where each starts.
        refused("^\\w(?:a?)*\\b(?:|b)+$", "\\w", "*", "\\b", "+")
    }

    @Test
    fun `empty-match rule - bounds are read as numbers (T2-34)`() {
        for (q in listOf("{01}", "{00}", "{001,1}", "{1,01}?", "{00,0}", "{0,00}", "{001,01}")) inside("^(?:\\d?)${q}x$")
        allowed("^(?:\\d?){01}x$", "5x")
        refused("^(?:\\d?){0,01}x$", "{0,01}")
        refused("^(?:\\d?){02}x$", "{02}")
        // The repeat rule (T3.56) reads them so too: {01} and {0,01} don't repeat, {02} does; {01} is
        // exactly once, refused by T2.58.
        refused("^(?:(?<amount>\\d)x){01}$", amount)
        inside("^(?:(?<amount>\\d)x){0,01}$")
        refused("^(?:(?<amount>\\d)x){02}$", amount)
    }

    @Test
    fun `empty-match rule - named or not, inside another group or not`() {
        refused("^(?<amount>\\d*)?x$", "?")
        refused("^(?:x(?:a*)+)y$", "+")
        refused("^(?:(?:a?)*b)+$", "*") // the outer group always holds b
        refused("^(?<kind>a?b?)+$", "+")
        // With T3.56 (a named group inside a group that repeats): both tokens, each in its place.
        refused("^(?:(?<amount>\\d*))+$", amount, "+")
        refused("^(?:(?<amount>\\d*),?)*$", amount, "*")
        // qa-24's T2.32 cases.
        refused("^(?<amount>(?:|\\d){1,1}){2}?[0-9,]$", "{2}?")
        refused("^(?:\\d*?)+(?<amount>\\d+)$", "+")
    }

    @Test
    fun `can match the empty string as REQ-DEV-041 defines it`() {
        // A character, ., an escape or a class can't.
        for (item in listOf("a", ".", "\\d", "[0-9]", "\\.", "\\u0041", "\\x41", "\\s")) inside("^(?:$item)+$")
        // Neither can a refused escape, {..}, } or ]: they count as characters.
        refused("^(?:\\w)+$", "\\w")
        refused("^(?:\\b)*a$", "\\b")
        refused("^(?:{x})+$", "{x}")
        refused("^(?:])+$", "]")
        refused("^(?:})+$", "}")
        // A lookahead can, and so can the refused zero-width items: ^ or $ out of place, lookbehind, (?i).
        refused("^(?=a)*a$", "*")
        refused("^(?!b){2}a$", "{2}")
        refused("^(?:(?!b))+a$", "+")
        allowed("^(?=\\d){1}(?<amount>\\d+)$", "12", "amount" to "12")
        refused("^(?:^)+a$", "^", "+")
        refused("^(?:a$)+$", "$")
        refused("^(?:$)+a$", "$", "+")
        refused("^(?:x^)+a$", "^")
        refused("^(?:(?<=a))+a$", "(?<=", "+")
        refused("^(?<=a)+a$", "(?<=", "+")
        refused("^(?:(?i))+a$", "(?i)", "+")
        refused("^(?:x(?i))+a$", "(?i)")
        // A quantified item can when its minimum is 0, else when the item can.
        refused("^(?:a{0,2})+$", "+")
        refused("^(?:a{0})+$", "+")
        refused("^(?:a{00,3})+$", "+")
        inside("^(?:a{1,2})+$")
        inside("^(?:a+b{1,2})+$")
        refused("^(?:(?:a?){2})+$", "{2}", "+") // the inner group is refused, and as an item it can
        refused("^(?:(?:a?){1})+$", "+")
        refused("^(?:(?:ab){0})+$", "+")
        inside("^(?:a(?:b?){1})+$")
        // Any other group, an atomic or inline-flag group included, can when any alternative can.
        refused("^(?>a?)+$", "(?>", "+")
        refused("^(?i:a|)+$", "(?i:", "+")
        refused("^(?i:a)+$", "(?i:")
        refused("^(?:a|b|)+$", "+")
        refused("^(?:|a)+$", "+")
        inside("^(?:a|b)+$")
        // An alternative can when every item in it can.
        refused("^(?:a?b*(?:c|))+$", "+")
        inside("^(?:a?b*c)+$")
    }

    @Test
    fun `a quantified group that always holds a character stays allowed, as in the official templates`() {
        allowed("^(?:(?<amount>[\\d,]+) ریال)?(?<kind>واریز|برداشت)$", "برداشت", "amount" to null)
        allowed("^(?:حساب:(?<account>\\d+)\\n)?مبلغ:(?<amount>[\\d,]+)$", "حساب:12\nمبلغ:5", "account" to "12", "amount" to "5")
    }

    @Test
    fun `lookahead rule - the doc's examples (T2-23)`() {
        refused("^(?:(?=(?<amount>\\d+))x|\\d+)$", amount)
        refused("^(?=(?:(?<amount>\\d+)|)?)\\d+$", amount, "?") // the inner group can match empty: ? too (T2.32)
        refused("^a??(?!(?<account>a))b(?<amount>\\d)$", "(?<account>")
        allowed("^(?!(?:x))(?<amount>\\d+)$", "12", "amount" to "12")
    }

    @Test
    fun `lookahead rule - at any depth, quantified or not`() {
        refused("^(?=(?<amount>\\d+))\\d+$", amount)
        refused("^(?!(?<kind>x))(?<amount>\\d+)$", "(?<kind>")
        refused("^(?=(?:(?:(?<amount>\\d)))\\d)\\d\\d$", amount)
        refused("^(?=x(?!(?:y|(?<sign>z))))x(?<amount>\\d)$", "(?<sign>")
        refused("^(?=(?<account>\\d)(?<amount>\\d))\\d\\d$", "(?<account>", amount)
        refused("^(?:(?=(?<amount>\\d+)){1})\\d+$", amount)
    }

    @Test
    fun `lookahead rule - outside a lookahead, and unnamed groups in one, stay allowed`() {
        allowed("^(?<amount>(?=\\d)\\d+)$", "12", "amount" to "12")
        allowed("^(?=\\d)(?<amount>\\d+)(?!x)$", "12", "amount" to "12")
        // REQ-PT-005's guard: unnamed groups only.
        allowed("^(?![\\s\\S]*(?:واریز|نشست|\\d\\+|\\+\\d))رمز (?<amount>\\d+)$", "رمز 12", "amount" to "12")
        // A plain group in a lookahead takes a number before the named group, as in JavaScript.
        allowed("^(?=(\\d))(?<amount>\\d+)$", "12", "amount" to "12")
    }

    @Test
    fun `lookahead rule - an opener several rules refuse is one token`() {
        refused("^(?:(?=(?<amount>\\d*)))?\\d+$", amount, "?") // lookahead; the quantified group can match empty (T2.32)
        refused("^(?:(?=(?<amount>\\d+))\\d)+$", amount) // lookahead and a group that can repeat
        refused("^(?<amount>\\d)(?=(?<amount>\\d))\\d$", amount) // lookahead and a reused name
        refused("^(?=(?<a_b>\\d))(?<amount>\\d)$", "(?<a_b>") // a bad name: its own token
        refused("^\\w(?=(?<amount>\\d))\\b\\d$", "\\w", amount, "\\b") // with other tokens, in the openers' order
    }

    @Test
    fun `a bad group name runs to the next angle bracket, past a parenthesis, as in the admin engine`() {
        refused("^(?<a)>b$", "(?<a)>")
        refused("^(?<amount\\d+)x>$", "(?<amount\\d+)x>")
        refused("^(?<amount\\d+)$", "(?<")
    }

    @Test
    fun `none of the dialect rules touches the rewrite of an allowed pattern`() {
        assertEquals("^([0-9]*){1}$", DialectTranslator("^(?<amount>\\d*){1}$").translate().java)
        assertEquals("^(?=([0-9]))([0-9]+)$", DialectTranslator("^(?=(\\d))(?<amount>\\d+)$").translate().java)
    }

    @Test
    fun `a quantified lookahead is wrapped in a non-capturing group, which ICU takes`() {
        // ICU refuses `(?=\d){1}` (Syntax error in regexp pattern); JavaScript and the JDK take it.
        assertEquals("^(?:(?=[0-9])){1}([0-9]+)$", DialectTranslator("^(?=\\d){1}(?<amount>\\d+)$").translate().java)
        assertEquals("^(?:(?!x(?:(?=y)){0,0}?)){01}x$", DialectTranslator("^(?!x(?=y){0,0}?){01}x$").translate().java)
        assertEquals("^(?=a)a$", DialectTranslator("^(?=a)a$").translate().java)
        allowed("^(?=\\d){1}(?<amount>\\d+)$", "12", "amount" to "12")
        allowed("^(?!a){0}a(?<amount>\\d)$", "a1", "amount" to "1")
        allowed("^(?=(\\d)){1,1}?(?<amount>\\d+)$", "12", "amount" to "12")
    }

    @Test
    fun `bound rule - the doc's refused examples, the quantifier as written as token (T2-59)`() {
        refused("^(?<amount>\\d{1,16777216})$", "{1,16777216}")
        refused("^\\d{1001}$", "{1001}")
        refused("^\\d{1001,}$", "{1001,}")
        refused("^\\d{0,1001}?$", "{0,1001}?")
        refused("^a{2147483648}$", "{2147483648}")
        refused("^\\d{2000,1}$", "{2000,1}") // n > m: dialect, not regex
        refused("^\\d{00000000000000000001001}$", "{00000000000000000001001}")
    }

    @Test
    fun `bound rule - the doc's allowed examples`() {
        allowed("^(?<amount>\\d{1,1000})$", "123", "amount" to "123")
        allowed("^\\d{0,01000}x$", "x")
        allowed("^\\d{0,01000}x$", "12x")
        allowed("^(?:\\d{1000})?x$", "x")
        assertEquals(null, PortableRegex.compile("^(?:\\d{1000})?x$").matchWhole("1x"))
        allowed("^\\d{00000000000000000001000}x$", "1".repeat(1000) + "x")
        allowed("^\\d{1000}$", "1".repeat(1000))
        assertEquals(null, PortableRegex.compile("^\\d{1000}$").matchWhole("1".repeat(1001)))
    }

    @Test
    fun `bound rule - with the other quantifier rules, one token each`() {
        // A possessive one is refused as possessive only.
        refused("^\\d{1001}+$", "}+")
        refused("^\\d{0,1001}+$", "}+")
        // Nothing to repeat: that rule's token alone; the ? after it is a quantifier of its own.
        refused("^{1001}?a$", "{1001}", "?")
        refused("^(?:{2000})a$", "{2000}")
        // The empty-match rule refuses the same quantifier: one token.
        refused("^(?:\\d?){1001}x$", "{1001}")
        refused("^(?:\\d?){1001}?x$", "{1001}?")
        refused("^(?=\\d){1001}\\d$", "{1001}")
        // With T3.56 and other tokens, each in its place.
        refused("^(?:(?<amount>\\d)x){1001}$", amount, "{1001}")
        refused("^\\w{1001}a{2,1002}\\b$", "\\w", "{1001}", "{2,1002}", "\\b")
    }

    @Test
    fun `bound rule - on anything`() {
        refused("^.{1001}$", "{1001}")
        refused("^[0-9]{1,1001}$", "{1,1001}")
        refused("^(?:ab){1001,}$", "{1001,}")
        refused("^(?<amount>\\d){5000}$", "{5000}")
        refused("^\\x41{9999}$", "{9999}")
        refused("^a{1001,1002}$", "{1001,1002}")
        refused("^a{0,10000}$", "{0,10000}")
        refused("^a{x}{1001}$", "{x}", "{1001}") // a refused {..} is something to repeat
    }

    @Test
    fun `bound rule - bounds are read as numbers, without overflow`() {
        for (q in listOf("{1000}", "{01000}", "{0001000}", "{00000000000000000001000}", "{0,1000}", "{1000,}", "{999,1000}", "{0}", "{000000000000000000000}")) {
            inside("^a$q$")
        }
        for (q in listOf("{1001}", "{01001}", "{10000}", "{99999999999999999999}", "{0,1001}", "{0,000000000000000001001}", "{1001,}", "{1,2147483648}", "{9223372036854775808}")) {
            refused("^a$q$", q)
            refused("^a$q?$", "$q?")
        }
        // n > m at or below 1000 is inside the dialect and doesn't compile (code regex), as before.
        inside("^\\d{3,1}$")
        inside("^\\d{1000,999}$")
        assertFailsWith<PortableRegex.InvalidPattern> { PortableRegex.compile("^\\d{3,1}$") }
        refused("^\\d{1001,1}$", "{1001,1}")
    }

    @Test
    fun `bound rule - every allowed bound compiles and matches on this platform`() {
        for (q in listOf("{1000}", "{0,1000}", "{1000,}", "{00000000000000000001000}", "{1,1000}?")) {
            assertNotNull(PortableRegex.compile("^a$q$").matchWhole("a".repeat(1000)), q)
        }
    }

    @Test
    fun `exactly-once rule - the doc's refused examples, the named group's opener as token (T2-58)`() {
        refused("^(?:(?:(?<amount>\\d\\d\\d) ریال){1}واریز|\\d+ ریال برداشت)$", amount)
        refused("^(?:(?:(?<amount>\\d)){1}x|\\d)$", amount)
        refused("^(?:(?<reference>(?<amount>\\d)){1}x|\\d)$", amount)
        refused("^(?:(?:(?<amount>\\d)){1,1}x|\\d)$", amount)
        refused("^(?:(?:(?<amount>\\d)){1}?x|\\d)$", amount)
        refused("^(?:(?:(?<amount>\\d)){01}x|\\d)$", amount)
        refused("^(?:(?:(?<amount>\\d+)){1}x|\\d+)$", amount)
    }

    @Test
    fun `exactly-once rule - the doc's allowed examples give no amount on 5, as in JavaScript and ICU`() {
        allowed("^(?:(?:(?<amount>\\d))?x|\\d)$", "5", "amount" to null)
        allowed("^(?:(?:(?<amount>\\d)){0,1}x|\\d)$", "5", "amount" to null)
        allowed("^(?:(?:(?<amount>\\d)){0}x|\\d)$", "5", "amount" to null)
        // A named group itself quantified {1} stays allowed.
        allowed("^(?:(?<amount>a){1}x|ab)$", "ab", "amount" to null)
        allowed("^(?:(?<amount>a){1}x|ab)$", "ax", "amount" to "a")
    }

    @Test
    fun `exactly-once rule - {1} and {1,1} by numeric value, lazy or not, at any depth, found at the quantifier`() {
        for (q in listOf("{1}", "{1,1}", "{01}", "{001,1}", "{1,01}", "{0001,0001}", "{1}?", "{1,1}?", "{01}?")) {
            refused("^(?:x(?<amount>\\d))$q$", amount)
            refused("^(?:((?:(?<amount>\\d)))y)${q}z$", amount)
        }
        for (q in listOf("?", "??", "{0}", "{0,0}", "{0,1}", "{00}", "{0,01}", "{0}?")) inside("^(?:x(?<amount>\\d))$q$")
        // {1,} and {1,2} can repeat (T3.56); the opener is one token either way.
        refused("^(?:x(?<amount>\\d)){1,}$", amount)
        refused("^(?:x(?<amount>\\d)){1,2}$", amount)
        // Every named group inside, each in its opener's place, with other tokens in order (T2.17).
        refused("^\\w(?:(?<account>\\d)-(?<amount>\\d)){1}\\b$", "\\w", "(?<account>", amount, "\\b")
        // A named group after the group, or around it, is fine.
        allowed("^(?<amount>(?:\\d){1})(?:x){1}(?<kind>y)$", "5xy", "amount" to "5", "kind" to "y")
        // A possessive {1}+ is refused as possessive, and the named group inside it too.
        refused("^(?:x(?<amount>\\d)){1}+$", amount, "}+")
        // A lookahead quantified {1}: the lookahead rule and this one refuse the same opener, one token.
        refused("^(?:(?=(?<amount>\\d+)){1})\\d+$", amount)
    }
}
