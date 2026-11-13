package io.github.asancart.android.capture

import io.github.asancart.android.core.capture.CaptureDecision
import io.github.asancart.android.core.capture.SmsProcessor
import io.github.asancart.android.core.capture.UnparsedSms
import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.profile.ProfileRepository
import io.github.asancart.android.core.queue.TransferReport
import io.github.asancart.android.core.sms.IncomingSms
import io.github.asancart.android.core.sms.Normalizer
import io.github.asancart.android.core.template.DefaultTemplateEngine
import io.github.asancart.android.core.template.ParserTemplate
import io.github.asancart.android.core.template.StoredTemplate
import io.github.asancart.android.core.template.TemplateSync
import io.github.asancart.android.log.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * SMS capture on the phone (REQ-APP-010..017). Every SMS, from the receiver or the inbox re-scan,
 * goes through the same [SmsProcessor] once: DEPOSIT → the Profile's outgoing queue, IGNORED →
 * dropped, anything else → the unparsed list. All work runs on one thread, in arrival order.
 *
 * Logs carry ids, template names, reasons and counts only: never SMS text, amounts or balances
 * (REQ-OPS-011).
 */
class CaptureService(
    private val store: CaptureStore,
    private val profiles: ProfileRepository,
    private val inbox: InboxSource,
    private val deviceApi: DeviceApi,
    private val log: AppLog,
    private val processor: SmsProcessor = SmsProcessor(DefaultTemplateEngine()),
    private val clock: () -> Instant = Instant::now,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "asancart-capture") }.asCoroutineDispatcher()

    /**
     * A failure in one job is logged, not thrown: an uncaught one would kill the app, and the same
     * SMS would kill it again on every start's re-scan. The SMS is not marked seen, so a fixed
     * app or template set handles it later.
     */
    private val onFailure = CoroutineExceptionHandler { _, e -> log.error("capture_failed", e) }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + onFailure)

    private val unparsedState = MutableStateFlow<List<UnparsedSms>>(emptyList())
    private val queueState = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** The unparsed list, newest first (REQ-APP-015). */
    val unparsed: StateFlow<List<UnparsedSms>> = unparsedState.asStateFlow()

    /** Pending Transfer Reports per Profile id (REQ-APP-014). */
    val queueSizes: StateFlow<Map<String, Int>> = queueState.asStateFlow()

    /** App start: templates if there are none yet, then the inbox re-scan (REQ-APP-011). */
    fun start(): Job = scope.launch {
        publish()
        if (store.templates().isEmpty()) refreshTemplatesNow()
        rescanNow()
    }

    /** From [SmsReceiver] (REQ-APP-010). */
    fun onReceived(messages: List<IncomingSms>): Job = scope.launch {
        for (sms in messages) handleOnce(sms)
        publish()
    }

    fun rescan(): Job = scope.launch { rescanNow() }

    /**
     * REQ-APP-015 re-read, on the user's request only: refresh the templates, then evaluate one
     * unparsed SMS ([id]) or the whole list again.
     */
    fun reread(id: String? = null): Job = scope.launch {
        refreshTemplatesNow()
        val templates = store.templates().map { it.template }
        val entries = store.unparsed().filter { id == null || it.id == id }
        for (u in entries) {
            val profile = profiles.profiles.value.firstOrNull { it.id == u.profileId } ?: continue
            when (val d = processor.reread(u.sms, profile, templates)) {
                is CaptureDecision.Report -> {
                    // Together: a report queued while its unparsed entry stays could be queued twice.
                    store.inTransaction {
                        store.enqueue(d.profileId, d.report, clock())
                        store.deleteUnparsed(u.id)
                    }
                    log.info("unparsed_reread", "result" to "report", "profile_id" to d.profileId, "template" to d.templateName)
                }
                is CaptureDecision.Ignored -> {
                    store.deleteUnparsed(u.id)
                    log.info("unparsed_reread", "result" to "ignored", "template" to d.templateName)
                }
                is CaptureDecision.Unparsed -> {
                    store.insertUnparsed(u.copy(reason = d.reason, templateName = d.templateName, templatesTried = d.templatesTried))
                    log.info("unparsed_reread", "result" to "unparsed", "reason" to d.reason)
                }
            }
        }
        publish()
    }

    /**
     * Stores a custom Parser Template made on the phone (DEC-014), replacing one with the same name.
     * The template editor (and REQ-APP-015's offer to re-read after saving) builds on this.
     */
    fun saveCustomTemplate(template: ParserTemplate): Job = scope.launch {
        val id = "custom/" + template.name
        val others = store.templates().filterNot { it.id == id }
        store.replaceTemplates(others + StoredTemplate(id, bankId = null, version = 1, custom = true, template = template))
    }

    /** «واریز نیست، حذفش کن»: the user says it isn't a deposit. */
    fun dismiss(id: String): Job = scope.launch {
        store.deleteUnparsed(id)
        publish()
    }

    /** The Profile's queued Transfer Reports, oldest first (sending: T4.4). */
    fun pending(profileId: String): List<TransferReport> = store.pending(profileId)

    /** Waits until everything queued before this call is done. */
    suspend fun idle() = withContext(dispatcher) {}

    private suspend fun refreshTemplatesNow() {
        val since = store.meta(CaptureStore.KEY_TEMPLATES_SINCE)?.let(Instant::parse)
        when (val r = deviceApi.parserTemplates(since)) {
            is ApiResult.Ok -> {
                val applied = TemplateSync.apply(store.templates(), r.value)
                store.replaceTemplates(applied.templates)
                store.setMeta(CaptureStore.KEY_TEMPLATES_SINCE, applied.since.toString())
                log.info("templates_refreshed", "items" to r.value.items.size, "stored" to applied.templates.size, "rejected" to applied.rejected.size)
            }
            is ApiResult.Error -> log.warn("templates_refresh_failed", "http" to r.httpStatus, "code" to r.code)
            is ApiResult.Unreachable -> log.warn("templates_refresh_failed", "reason" to "unreachable")
        }
    }

    private fun rescanNow() {
        val now = clock()
        val checkpoint = store.meta(CaptureStore.KEY_INBOX_CHECKPOINT)?.toLongOrNull()
        if (checkpoint == null) {
            // First start: SMS from before the app was installed are history, not deposits to report.
            store.setMeta(CaptureStore.KEY_INBOX_CHECKPOINT, now.toEpochMilli().toString())
            return
        }
        val rows = inbox.readSince(checkpoint)
        var handled = 0
        for (row in rows) if (handleOnce(IncomingSms(row.address, row.body, Instant.ofEpochMilli(row.dateMillis)))) handled++
        rows.maxOfOrNull { it.dateMillis }?.let { store.setMeta(CaptureStore.KEY_INBOX_CHECKPOINT, it.toString()) }
        store.pruneSeen(now.minus(SEEN_KEEP))
        if (rows.isNotEmpty()) log.info("inbox_rescan", "rows" to rows.size, "handled" to handled)
        publish()
    }

    /** Handles [sms] unless the receiver or an earlier re-scan already did. True when handled now. */
    private fun handleOnce(sms: IncomingSms): Boolean {
        val fp = fingerprint(sms)
        if (store.seenNear(fp, sms.receivedAt, SAME_SMS_WINDOW.toMillis())) return false
        val templates = store.templates().map { it.template }
        val decisions = processor.process(sms, profiles.profiles.value, templates)
        // One transaction: a crash before the SMS is marked seen leaves no queued report either, so
        // the next re-scan (REQ-APP-011) queues it once, not again under a new client_report_id.
        store.inTransaction {
            for (d in decisions) {
                when (d) {
                    is CaptureDecision.Report -> store.enqueue(d.profileId, d.report, clock())
                    is CaptureDecision.Ignored -> Unit
                    is CaptureDecision.Unparsed -> store.insertUnparsed(UnparsedSms(UUID.randomUUID().toString(), d.profileId, sms.sender, sms.body, sms.receivedAt, d.reason, d.templateName, d.templatesTried))
                }
            }
            store.markSeen(fp, sms.receivedAt)
        }
        for (d in decisions) {
            when (d) {
                is CaptureDecision.Report -> log.info("report_queued", "profile_id" to d.profileId, "template" to d.templateName)
                is CaptureDecision.Ignored -> log.info("sms_ignored", "profile_id" to d.profileId, "template" to d.templateName)
                is CaptureDecision.Unparsed -> log.info("sms_unparsed", "profile_id" to d.profileId, "reason" to d.reason)
            }
        }
        return true
    }

    private fun publish() {
        unparsedState.value = store.unparsed()
        queueState.value = store.pendingCounts()
    }

    companion object {
        /** The receiver's time and the inbox's stored time of one SMS differ by seconds at most. */
        val SAME_SMS_WINDOW: Duration = Duration.ofMinutes(15)
        private val SEEN_KEEP: Duration = Duration.ofDays(30)

        /** SHA-256 of the normalized sender and the text; the text itself is not stored for it. */
        fun fingerprint(sms: IncomingSms): String {
            val d = MessageDigest.getInstance("SHA-256").digest((Normalizer.normalizeSender(sms.sender) + "\n" + sms.body).toByteArray())
            return d.joinToString("") { "%02x".format(it) }
        }
    }
}
