package io.github.asancart.android.core.sms

import io.github.asancart.android.core.deviceapi.PairRequest
import io.github.asancart.android.core.deviceapi.PairResponse
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.template.TemplateResult
import io.github.asancart.android.core.template.TemplateStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-OPS-003, REQ-OPS-011: objects that may end up in a log line never print SMS text, balances or secrets. */
class NoSecretsInToStringTest {
    private val body = "بانک ملت\nحساب1122334455\nواریز2,750,000\nمانده20,230,000\n05/08/20-13:55"

    @Test
    fun `IncomingSms hides the SMS text and balance`() {
        val text = IncomingSms("Bank Mellat", body, Instant.parse("2026-11-12T10:25:00Z")).toString()
        assertFalse(text.contains("مانده"), text)
        assertFalse(text.contains("20,230,000"), text)
        assertFalse(text.contains("1122334455"), text)
        assertTrue(text.contains("Bank Mellat"), text)
    }

    @Test
    fun `TemplateResult hides account digits and the SMS text`() {
        val result = TemplateResult(
            templateName = "mellat-account",
            status = TemplateStatus.DEPOSIT,
            amount = 2_750_000,
            account = "1122334455",
            normalizedText = body,
        )
        val text = result.toString()
        assertFalse(text.contains("1122334455"), text)
        assertFalse(text.contains("20,230,000"), text)
    }

    @Test
    fun `PairRequest hides the pairing code`() {
        val text = PairRequest(pairingCode = "K7QM-2XPA", deviceName = "Galaxy A54", appVersion = "0.1.0").toString()
        assertFalse(text.contains("K7QM-2XPA", ignoreCase = true), text)
        assertTrue(text.contains("Galaxy A54"), text)
    }

    @Test
    fun `PairResponse hides the Device credential`() {
        val text = PairResponse("dev_1", "secret-credential-123", ShopRef("s", "n"), emptyList(), emptyList()).toString()
        assertFalse(text.contains("secret-credential-123"), text)
    }
}
