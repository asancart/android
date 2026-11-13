package io.github.asancart.android.capture

import io.github.asancart.android.core.capture.UnparsedReason
import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.Bank
import io.github.asancart.android.core.deviceapi.Card
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.ParserTemplateItem
import io.github.asancart.android.core.deviceapi.ParserTemplatesResponse
import io.github.asancart.android.core.deviceapi.ShopRef
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CardBinding
import io.github.asancart.android.core.profile.Connection
import io.github.asancart.android.core.profile.InMemoryProfileRepository
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.log.AppLog
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** REQ-APP-010, REQ-APP-011, REQ-APP-015 on the app's real storage (SQLite under Robolectric). */
@RunWith(RobolectricTestRunner::class)
class CaptureServiceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val t0 = Instant.parse("2026-11-12T10:00:00Z")
    // Sample SMS of the design prototype (design/prototype/app.js).
    private val deposit = "\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644:25,000,000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:198,088,329\n0820-14:21"
    private val unknown = "\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644 \u0627\u0632 6219:5,000,000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:203,088,329\n0820-09:14"

    private val profiles = InMemoryProfileRepository(ApiTarget.platform("http://10.0.2.2:8080")).apply {
        save(
            Profile(
                id = "p1",
                name = "p",
                target = ApiTarget.platform("http://10.0.2.2:8080"),
                connection = Connection("d", "phone", ShopRef("s", "shop"), listOf(Card("c1", "ck_melli", "6037 99\u2022\u2022 \u2022\u2022\u2022\u2022 4821", "melli")), listOf(Bank("melli", "\u0645\u0644\u06CC", emptyList()))),
                cardBindings = listOf(CardBinding("700717", "melli-account", listOf("ck_melli"))),
                enabled = true,
                enabledAt = t0.minusSeconds(3600),
            ),
        )
    }

    private class FakeInbox : InboxSource {
        val rows = mutableListOf<InboxSms>()
        var failNext = false

        override fun readSince(afterMillis: Long): List<InboxSms> {
            if (failNext) {
                failNext = false
                throw IllegalStateException("inbox unavailable")
            }
            return rows.filter { it.dateMillis > afterMillis }.sortedBy { it.dateMillis }
        }
    }

    private class Events : AppLog {
        val errors = mutableListOf<String>()

        override fun info(event: String, vararg fields: Pair<String, Any?>) = Unit

        override fun warn(event: String, vararg fields: Pair<String, Any?>) = Unit

        override fun error(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) {
            errors += event
        }
    }

    /** One sample template (written for these tests), or none until [ready]. */
    private class Templates(var ready: Boolean = true) : DeviceApi by StubDeviceApi() {
        private val body =
            """{"schema_version":1,"name":"sample-melli","sender_patterns":["700717"],""" +
                """"pattern":"^بانک ملی ایران\\nانتقالی?:(?<amount>[\\d,]+)(?<sign>[+-])\\nحساب:(?<account>\\d+)\\nمانده:-?[\\d,]+\\n\\d{4}-\\d{2}:\\d{2}$",""" +
                """"direction":{"mode":"sign"},"priority":10}"""

        override suspend fun parserTemplates(since: Instant?): ApiResult<ParserTemplatesResponse> {
            val items = if (ready && since == null) {
                listOf(ParserTemplateItem("sample/1", "melli", "sample-melli", 1, ParserTemplateItem.Status.PUBLISHED, Instant.EPOCH, null, body))
            } else {
                emptyList()
            }
            return ApiResult.Ok(ParserTemplatesResponse(if (ready) Instant.EPOCH.plusSeconds(1) else Instant.EPOCH, items))
        }
    }

    private val inbox = FakeInbox()

    private fun service(store: CaptureStore = CaptureStore(context, null), api: DeviceApi = Templates(), now: Instant = t0, log: AppLog = AppLog.forBuild(false)) =
        CaptureService(store, profiles, inbox, api, log, clock = { now })

    private fun CaptureService.sync(block: CaptureService.() -> Unit) = runBlocking {
        block()
        idle()
    }

    @Test
    fun firstStartDoesNotImportInboxHistory() {
        inbox.rows += InboxSms(1, "700717", deposit, t0.minusSeconds(60).toEpochMilli())
        val store = CaptureStore(context, null)
        service(store).sync { start() }
        assertTrue(store.pendingCounts().isEmpty())
        assertEquals(t0.toEpochMilli().toString(), store.meta(CaptureStore.KEY_INBOX_CHECKPOINT))
    }

    @Test
    fun rescanHandlesNewInboxRowsOnceAndMovesTheCheckpoint() {
        val store = CaptureStore(context, null)
        val s = service(store)
        s.sync { start() }
        inbox.rows += InboxSms(1, "700717", deposit, t0.plusSeconds(10).toEpochMilli())
        inbox.rows += InboxSms(2, "700717", unknown, t0.plusSeconds(20).toEpochMilli())
        inbox.rows += InboxSms(3, "Bank Mellat", "\u0633\u0644\u0627\u0645", t0.plusSeconds(30).toEpochMilli())
        s.sync { rescan() }
        s.sync { rescan() }
        assertEquals(mapOf("p1" to 1), store.pendingCounts())
        assertEquals(25_000_000L, store.pending("p1").single().amount)
        assertEquals(t0.plusSeconds(10), store.pending("p1").single().smsTime)
        val u = s.unparsed.value.single()
        assertEquals(UnparsedReason.NO_TEMPLATE_MATCHED, u.reason)
        assertEquals(t0.plusSeconds(30).toEpochMilli().toString(), store.meta(CaptureStore.KEY_INBOX_CHECKPOINT))
    }

    @Test
    fun theReceiverAndTheRescanNeverHandleOneSmsTwice() {
        val store = CaptureStore(context, null)
        val s = service(store)
        s.sync { start() }
        s.sync { onReceived(listOf(IncomingSms("700717", deposit, t0.plusSeconds(5)))) }
        // The SMS app stores it a moment later; the next start re-scans it.
        inbox.rows += InboxSms(1, "700717", deposit, t0.plusSeconds(6).toEpochMilli())
        s.sync { rescan() }
        assertEquals(1, store.pending("p1").size)
    }

    @Test
    fun aFailingJobIsLoggedAndRetriedNotFatal() {
        val store = CaptureStore(context, null)
        val events = Events()
        val s = service(store, log = events)
        s.sync { start() }
        val checkpoint = store.meta(CaptureStore.KEY_INBOX_CHECKPOINT)
        inbox.rows += InboxSms(1, "700717", deposit, t0.plusSeconds(10).toEpochMilli())
        inbox.failNext = true
        // An uncaught failure would end the app process, and the next start's re-scan would end it again.
        s.sync { rescan() }
        assertEquals(listOf("capture_failed"), events.errors)
        assertEquals(checkpoint, store.meta(CaptureStore.KEY_INBOX_CHECKPOINT))
        s.sync { rescan() }
        assertEquals(1, store.pending("p1").size)
    }

    @Test
    fun aProfileThatIsOffCapturesNothing() {
        profiles.setEnabled("p1", false)
        val store = CaptureStore(context, null)
        val s = service(store)
        s.sync { onReceived(listOf(IncomingSms("700717", deposit, t0))) }
        assertTrue(store.pendingCounts().isEmpty())
        assertTrue(s.unparsed.value.isEmpty())
    }

    @Test
    fun rereadQueuesAReportOnceTemplatesArriveAndDismissRemoves() {
        val api = Templates(ready = false)
        val store = CaptureStore(context, null)
        val s = service(store, api)
        s.sync { onReceived(listOf(IncomingSms("700717", deposit, t0), IncomingSms("700717", unknown, t0.plusSeconds(1)))) }
        assertEquals(setOf(UnparsedReason.NO_TEMPLATES), s.unparsed.value.map { it.reason }.toSet())

        api.ready = true
        val first = s.unparsed.value.first { it.body == deposit }.id
        s.sync { reread(first) }
        assertEquals(1, store.pending("p1").size)
        assertEquals(t0, store.pending("p1").single().smsTime)
        assertNull(s.unparsed.value.firstOrNull { it.id == first })

        val other = s.unparsed.value.single()
        assertEquals(UnparsedReason.NO_TEMPLATES, other.reason)
        s.sync { reread() }
        assertEquals(UnparsedReason.NO_TEMPLATE_MATCHED, s.unparsed.value.single().reason)
        s.sync { dismiss(other.id) }
        assertTrue(s.unparsed.value.isEmpty())
    }

    @Test
    fun theUnparsedListSurvivesARestart() {
        val name = "capture-test.db"
        context.deleteDatabase(name)
        service(CaptureStore(context, name)).sync { onReceived(listOf(IncomingSms("700717", unknown, t0))) }
        val restarted = service(CaptureStore(context, name))
        restarted.sync { start() }
        assertEquals(unknown, restarted.unparsed.value.single().body)
    }
}
