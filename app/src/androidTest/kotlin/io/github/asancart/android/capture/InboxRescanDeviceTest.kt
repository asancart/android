package io.github.asancart.android.capture

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.asancart.android.core.capture.UnparsedReason
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.InMemoryProfileRepository
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.log.AppLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * REQ-APP-011 on a device: the re-scan reads the real system inbox. CI sends an SMS to the
 * emulator first (`adb emu sms send`) and passes its sender and text as instrumentation
 * arguments `inboxSender` and `inboxText`; without them this test is skipped.
 */
@RunWith(AndroidJUnit4::class)
class InboxRescanDeviceTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theRescanReadsTheInboxFromTheCheckpoint() {
        val sender = args.getString("inboxSender")
        val text = args.getString("inboxText")
        assumeTrue("no SMS was sent to this device for the test", sender != null && text != null)

        val inbox = AndroidInbox(context, SmsPermissions(context))
        val row = inbox.readSince(0).lastOrNull { it.body == text }
        assertTrue("the SMS sent by CI is not in the inbox", row != null)

        val profiles = InMemoryProfileRepository(ApiTarget.platform("http://10.0.2.2:8080")).apply {
            save(Profile(id = "p", name = "p", target = ApiTarget.platform("http://10.0.2.2:8080"), cardBindings = listOf(CardBinding(sender!!, "none", listOf("ck"))), enabled = true, enabledAt = Instant.EPOCH))
        }
        val store = CaptureStore(context, null)
        store.setMeta(CaptureStore.KEY_INBOX_CHECKPOINT, (row!!.dateMillis - 1).toString())
        val capture = CaptureService(store, profiles, inbox, StubDeviceApi(), AppLog.forBuild(false))
        runBlocking {
            capture.rescan()
            capture.idle()
        }
        // No official template knows this sender: kept, never dropped (REQ-APP-015).
        val u = capture.unparsed.value.single { it.body == text }
        assertEquals(UnparsedReason.NO_TEMPLATES, u.reason)
        assertEquals(row.dateMillis.toString(), store.meta(CaptureStore.KEY_INBOX_CHECKPOINT))
    }
}
