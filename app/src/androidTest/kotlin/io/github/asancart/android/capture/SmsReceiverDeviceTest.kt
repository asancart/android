package io.github.asancart.android.capture

import android.content.Intent
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asancart.android.AsancartApp
import io.github.asancart.android.core.capture.UnparsedReason
import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Connection
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.template.Direction
import io.github.asancart.android.core.template.ParserTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * REQ-APP-010 on a device: real SMS-DELIVER PDUs, read by the framework's
 * `Telephony.Sms.Intents.getMessagesFromIntent`, go through the manifest receiver into the
 * app's capture (templates from the stub Device API, storage in the app's database).
 */
@RunWith(AndroidJUnit4::class)
class SmsReceiverDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as AsancartApp).container
    private val profileId = "device-test-" + UUID.randomUUID()
    private val sender = "700717"

    /** A template written for this test (the official ones are not bundled in CI builds). */
    private val sample = ParserTemplate(
        name = "device-test-melli",
        senderPatterns = listOf(sender),
        pattern = "^\u0628\u0627\u0646\u06A9 \u0645\u0644\u06CC \u0627\u06CC\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644\u06CC?:(?<amount>[\\d,]+)(?<sign>[+-])\n\u062D\u0633\u0627\u0628:(?<account>\\d+)\n\u0645\u0627\u0646\u062F\u0647:-?[\\d,]+\n\\d{4}-\\d{2}:\\d{2}$",
        direction = Direction.Sign,
        priority = 10,
    )

    @Before
    fun bindMelli() {
        runBlocking {
            container.capture.saveCustomTemplate(sample)
            container.capture.idle()
        }
        container.profiles.save(
            Profile(
                id = profileId,
                name = "test",
                target = ApiTarget.platform("http://10.0.2.2:8080"),
                connection = Connection("d", "phone", ShopRef("s", "shop"), listOf(Card("c", "ck_melli", "6037 99\u2022\u2022 \u2022\u2022\u2022\u2022 4821", "melli")), listOf(Bank("melli", "\u0645\u0644\u06CC", emptyList()))),
                cardBindings = listOf(CardBinding(sender, sample.name, listOf("ck_melli"))),
                enabled = true,
                enabledAt = Instant.EPOCH,
            ),
        )
    }

    private fun deliver(text: String, maxCharsPerPart: Int = 70) {
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .putExtra("pdus", Pdus.deliver(sender, text, maxCharsPerPart).toTypedArray<Any>())
            .putExtra("format", "3gpp")
        SmsReceiver().onReceive(context, intent)
        runBlocking { container.capture.idle() }
    }

    @Test
    fun aDepositSmsBecomesOneQueuedReport() {
        val unique = (100_000..999_999).random()
        deliver("\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644:${unique},000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:198,088,329\n0820-14:21")
        val report = container.capture.pending(profileId).single()
        assertEquals(unique * 1000L, report.amount)
        assertEquals(listOf("ck_melli"), report.cardKeys)
    }

    @Test
    fun aLongSmsInSeveralPartsIsJoined() {
        val unique = (100_000..999_999).random()
        deliver("\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644:${unique},000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:198,088,329\n0820-14:21", maxCharsPerPart = 20)
        assertEquals(unique * 1000L, container.capture.pending(profileId).single().amount)
    }

    @Test
    fun anUnknownLayoutIsKeptInTheUnparsedList() {
        val marker = UUID.randomUUID().toString().take(8)
        deliver("\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u067E\u06CC\u0627\u0645 \u062A\u0627\u0632\u0647 $marker\n\u062D\u0633\u0627\u0628:10000")
        val u = container.capture.unparsed.value.single { it.profileId == profileId }
        assertEquals(UnparsedReason.NO_TEMPLATE_MATCHED, u.reason)
        assertTrue(u.body.contains(marker))
    }
}
