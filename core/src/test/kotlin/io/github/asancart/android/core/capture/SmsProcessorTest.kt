package io.github.asancart.android.core.capture

import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Connection
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.Direction
import io.github.asancart.android.core.template.ParserTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmsProcessorTest {
    private val processor = SmsProcessor(DefaultTemplateEngine(), newReportId = { "r1" })
    private val t0 = Instant.parse("2026-11-12T10:00:00Z")

    private val deposit = ParserTemplate(
        name = "mellat-account",
        senderPatterns = listOf("Bank Mellat"),
        pattern = "^\u062D\u0633\u0627\u0628(?<account>\\d+)\\n(?<kind>\u0648\u0627\u0631\u06CC\u0632|\u0628\u0631\u062F\u0627\u0634\u062A)(?<amount>[\\d,]+)\\n\u0645\u0627\u0646\u062F\u0647[\\d,]+$",
        direction = Direction.Keyword(listOf("\u0648\u0627\u0631\u06CC\u0632"), listOf("\u0628\u0631\u062F\u0627\u0634\u062A")),
        priority = 10,
    )
    private val otp = ParserTemplate(
        name = "mellat-otp",
        senderPatterns = listOf("Bank Mellat"),
        pattern = "^[\\s\\S]*\u0631\u0645\u0632[\\s\\S]*$",
        direction = Direction.Fixed(deposit = false),
        priority = 900,
    )
    private val templates = listOf(deposit, otp)

    private fun profile(
        id: String = "p1",
        enabled: Boolean = true,
        enabledAt: Instant? = t0.minusSeconds(60),
        bindings: List<CardBinding> = listOf(CardBinding("Bank Mellat", "mellat-account", listOf("ck_mellat"))),
        required: List<String> = emptyList(),
    ) = Profile(
        id = id,
        name = id,
        target = ApiTarget.platform("http://10.0.2.2:8080"),
        connection = Connection(
            deviceId = "d",
            deviceName = "phone",
            shop = ShopRef("s", "shop"),
            cards = listOf(Card("c1", "ck_mellat", "6104 33\u2022\u2022 \u2022\u2022\u2022\u2022 1907", "mellat")),
            banks = listOf(Bank("mellat", "\u0628\u0627\u0646\u06A9 \u0645\u0644\u062A", required)),
        ),
        cardBindings = bindings,
        enabled = enabled,
        enabledAt = enabledAt,
    )

    private fun sms(body: String, sender: String = "BANK MELLAT", at: Instant = t0) = IncomingSms(sender, body, at)

    private val depositText = "\u062D\u0633\u0627\u06288065955489\n\u0648\u0627\u0631\u06CC\u06322,250,000\n\u0645\u0627\u0646\u062F\u064723,236,833"

    @Test
    fun `a deposit on a bound sender becomes one Transfer Report with the binding's Card Keys`() {
        val d = assertIs<CaptureDecision.Report>(processor.process(sms(depositText), listOf(profile()), templates).single())
        assertEquals("p1", d.profileId)
        assertEquals(listOf("ck_mellat"), d.report.cardKeys)
        assertEquals(2_250_000L, d.report.amount)
        assertEquals(t0, d.report.smsTime)
        assertEquals("r1", d.report.clientReportId)
    }

    @Test
    fun `withdrawals and OTPs are ignored, unknown layouts are kept`() {
        assertIs<CaptureDecision.Ignored>(processor.process(sms("\u062D\u0633\u0627\u06281\n\u0628\u0631\u062F\u0627\u0634\u062A5\n\u0645\u0627\u0646\u062F\u06479"), listOf(profile()), templates).single())
        assertIs<CaptureDecision.Ignored>(processor.process(sms("\u0631\u0645\u0632 \u0634\u0645\u0627 1234"), listOf(profile()), templates).single())
        val u = assertIs<CaptureDecision.Unparsed>(processor.process(sms("\u0648\u0627\u0631\u06CC\u0632 \u0634\u0628\u0627 2,000,000"), listOf(profile()), templates).single())
        assertEquals(UnparsedReason.NO_TEMPLATE_MATCHED, u.reason)
        assertEquals(listOf("mellat-account", "mellat-otp"), u.templatesTried)
    }

    @Test
    fun `a missing required field keeps the deposit in unparsed (REQ-DEV-056)`() {
        val u = assertIs<CaptureDecision.Unparsed>(processor.process(sms(depositText), listOf(profile(required = listOf("reference"))), templates).single())
        assertEquals(UnparsedReason.MISSING_REQUIRED_FIELD, u.reason)
    }

    @Test
    fun `off, turned on later, or not bound means nothing is kept`() {
        assertTrue(processor.process(sms(depositText), listOf(profile(enabled = false, enabledAt = null)), templates).isEmpty())
        assertTrue(processor.process(sms(depositText), listOf(profile(enabledAt = t0.plusSeconds(1))), templates).isEmpty())
        assertTrue(processor.process(sms(depositText, sender = "700717"), listOf(profile()), templates).isEmpty())
    }

    @Test
    fun `each Profile that binds the sender decides on its own`() {
        val decisions = processor.process(sms(depositText), listOf(profile("p1"), profile("p2"), profile("p3", enabled = false)), templates)
        assertEquals(listOf("p1", "p2"), decisions.map { it.profileId })
    }

    @Test
    fun `two bindings for one sender are ambiguous, no template is NO_TEMPLATES`() {
        val two = listOf(CardBinding("Bank Mellat", "mellat-account", listOf("a")), CardBinding("bankmellat", "mellat-account", listOf("b")))
        assertEquals(UnparsedReason.AMBIGUOUS_BINDING, assertIs<CaptureDecision.Unparsed>(processor.process(sms(depositText), listOf(profile(bindings = two)), templates).single()).reason)
        assertEquals(UnparsedReason.NO_TEMPLATES, assertIs<CaptureDecision.Unparsed>(processor.process(sms(depositText), listOf(profile()), emptyList()).single()).reason)
    }

    @Test
    fun `re-read runs on the user's request even with the switch off`() {
        assertIs<CaptureDecision.Report>(processor.reread(sms(depositText), profile(enabled = false, enabledAt = null), templates))
        assertEquals(UnparsedReason.NO_BINDING, assertIs<CaptureDecision.Unparsed>(processor.reread(sms(depositText), profile(bindings = emptyList()), templates)).reason)
    }

    @Test
    fun `the binding's custom template joins the sender's set`() {
        val custom = deposit.copy(name = "my-mellat", senderPatterns = listOf("98999"))
        val binding = CardBinding("Bank Mellat", "my-mellat", listOf("ck"))
        assertEquals(listOf("mellat-account", "mellat-otp", "my-mellat"), processor.templatesFor(binding, "Bank Mellat", templates + custom).map { it.name })
    }
}
