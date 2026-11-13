package io.github.asancart.android.core.template

import io.github.asancart.android.core.sms.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** The template engine of the app. Compiled patterns are cached by source and flags. */
class DefaultTemplateEngine : TemplateEngine {
    private val compiled = ConcurrentHashMap<String, Compiled>()

    private sealed interface Compiled {
        class Ok(val regex: PortableRegex) : Compiled

        class Failed(val message: String) : Compiled
    }

    private fun compile(template: ParserTemplate): Compiled {
        val flags = template.flags.filter { it == 'i' || it == 's' }
        return compiled.getOrPut(flags + "\u0000" + template.pattern) {
            try {
                Compiled.Ok(PortableRegex.compile(template.pattern, flags))
            } catch (e: PortableRegex.InvalidPattern) {
                Compiled.Failed(e.message ?: "invalid pattern")
            }
        }
    }

    override fun run(template: ParserTemplate, smsText: String, sender: String?, requiredFields: List<String>): TemplateResult {
        val normalized = Normalizer.normalizeText(smsText, template.normalize)
        fun out(status: TemplateStatus, matched: Boolean = false, fields: Fields = Fields(), direction: MoneyDirection? = null, detail: String? = null) =
            TemplateResult(
                templateName = template.name,
                status = status,
                matched = matched,
                direction = direction,
                amount = fields.amount,
                account = fields.account,
                reference = fields.reference,
                kind = fields.kind,
                channel = fields.channel,
                normalizedText = normalized,
                detail = detail,
            )

        if (sender != null && Normalizer.jsTrim(sender).isNotEmpty() && !Normalizer.senderMatches(template.senderPatterns, sender)) {
            return out(TemplateStatus.SENDER_MISMATCH)
        }

        val regex = when (val c = compile(template)) {
            is Compiled.Failed -> return out(TemplateStatus.INVALID_TEMPLATE, detail = c.message)
            is Compiled.Ok -> c.regex
        }

        val m = regex.matchWhole(normalized) ?: return out(TemplateStatus.NO_MATCH)

        // As the reference engine: an empty capture is no capture, except for amount.
        var fields = Fields(
            account = m["account"]?.ifEmpty { null },
            reference = m["reference"]?.ifEmpty { null },
            kind = m["kind"]?.ifEmpty { null },
            channel = m["channel"]?.ifEmpty { null },
        )
        val rawAmount = m["amount"]
        if (rawAmount != null) {
            val amount = parseAmount(rawAmount, template.amountUnit)
                ?: return out(TemplateStatus.BAD_AMOUNT, matched = true, fields = fields, detail = rawAmount)
            fields = fields.copy(amount = amount)
        }

        val direction: MoneyDirection? = when (val d = template.direction) {
            is Direction.Fixed -> if (d.deposit) MoneyDirection.IN else MoneyDirection.OUT
            Direction.Sign -> when (Normalizer.jsTrim(m["sign"] ?: "")) {
                "+" -> MoneyDirection.IN
                "-" -> MoneyDirection.OUT
                else -> null
            }
            is Direction.Keyword -> {
                val kind = Normalizer.normalizeLetters(Normalizer.jsTrim(m["kind"] ?: ""))
                fun eq(w: String) = Normalizer.normalizeLetters(Normalizer.jsTrim(w)) == kind
                when {
                    d.incoming.any(::eq) -> MoneyDirection.IN
                    d.outgoing.any(::eq) -> MoneyDirection.OUT
                    else -> null
                }
            }
        }

        if (direction == null) return out(TemplateStatus.DIRECTION_UNKNOWN, matched = true, fields = fields)
        if (direction == MoneyDirection.OUT) return out(TemplateStatus.IGNORED, matched = true, fields = fields, direction = direction)
        if (fields.amount == null) return out(TemplateStatus.BAD_AMOUNT, matched = true, fields = fields, direction = direction, detail = "")

        val missing = requiredFields.filter { field ->
            when (field) {
                "amount" -> fields.amount == null
                "account" -> fields.account == null
                "reference" -> fields.reference == null
                "kind" -> fields.kind == null
                "channel" -> fields.channel == null
                else -> true
            }
        }
        return out(TemplateStatus.DEPOSIT, matched = true, fields = fields, direction = direction).copy(missingRequired = missing)
    }

    override fun runSet(templates: List<ParserTemplate>, smsText: String, sender: String?, requiredFields: List<String>): SetResult {
        val order = templates.indices.sortedWith(compareBy<Int> { templates[it].priority }.thenBy { it })
        val results = arrayOfNulls<TemplateResult>(templates.size)
        var winner: Int? = null
        for (i in order) {
            val r = run(templates[i], smsText, sender, requiredFields)
            results[i] = r
            if (winner == null && r.matched) winner = i
        }
        return SetResult(winner, results.map { it!! }, order)
    }

    private data class Fields(
        val amount: Long? = null,
        val account: String? = null,
        val reference: String? = null,
        val kind: String? = null,
        val channel: String? = null,
    )

    companion object {
        /** JavaScript `Number.MAX_SAFE_INTEGER`, the reference engine's limit. */
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

        /**
         * REQ-DEV-054: digits to Latin; `,`, U+066C and whitespace removed; must be `^\d+$` and
         * positive; toman × 10. Null when it isn't a positive integer.
         */
        fun parseAmount(raw: String, unit: AmountUnit = AmountUnit.RIAL): Long? {
            val digits = Normalizer.normalizeDigits(raw).filterNot { it == ',' || it == '\u066C' || Normalizer.isJsWhitespace(it) }
            if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
            val n = digits.trimStart('0').let { if (it.length > 16) return null else it.ifEmpty { "0" }.toLong() }
            if (n <= 0 || n > MAX_SAFE_INTEGER) return null
            return n * unit.toRials
        }
    }
}
