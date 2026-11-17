package io.github.asancart.android.log

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import io.github.asancart.android.AppContainer
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.CrashReport
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.HttpDeviceApi
import io.github.asancart.android.profile.KeystoreCredentialStore
import kotlinx.coroutines.runBlocking
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.ReportingAdministrator
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderException
import org.acra.sender.ReportSenderFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Who gets crash reports (REQ-OPS-013): only Profiles whose user turned it on. Default off for
 * custom targets; the Asancart Profile is asked once (my call in the docs). Kept in its own
 * preferences file, because ACRA sends from its own process.
 */
// commit(), not apply(): ACRA reads these from its own process right after a crash.
@SuppressLint("ApplySharedPref")
class CrashReportPrefs(private val prefs: SharedPreferences) {
    /** Opted-in Profile id → its target base URL. */
    fun optedIn(): Map<String, String> = prefs.all.filterKeys { it.startsWith(PREFIX) }
        .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(PREFIX) to it } }
        .toMap()

    fun isOptedIn(profileId: String): Boolean = prefs.contains(PREFIX + profileId)

    fun setOptIn(profileId: String, baseUrl: String, on: Boolean) {
        val e = prefs.edit()
        if (on) e.putString(PREFIX + profileId, baseUrl) else e.remove(PREFIX + profileId)
        e.putBoolean(ASKED, true).commit()
    }

    /** A removed Profile (REQ-APP-040): its opt-in goes; whether the user was asked stays. */
    fun forget(profileId: String) {
        prefs.edit().remove(PREFIX + profileId).commit()
    }

    /** True once the user answered for the Asancart Profile (or set any switch). */
    val asked: Boolean get() = prefs.getBoolean(ASKED, false)

    fun markAsked() {
        prefs.edit().putBoolean(ASKED, true).commit()
    }

    companion object {
        private const val PREFIX = "optin:"
        private const val ASKED = "platform_asked"

        fun of(context: Context) = CrashReportPrefs(context.getSharedPreferences("crash_reports", Context.MODE_PRIVATE))
    }
}

/**
 * Sends one crash to every opted-in Profile's target (`POST /v1/device/crash-reports`,
 * REQ-DEV-057). The stack trace is redacted (REQ-OPS-003) and cut to 64 KB. With no opt-in nothing
 * is sent at all.
 */
class CrashUploader(private val apiFor: (profileId: String, baseUrl: String) -> DeviceApi) {
    /** Number of targets that accepted it; null when every send failed (ACRA keeps it to retry). */
    fun upload(optedIn: Map<String, String>, crash: CrashReport): Int? {
        if (optedIn.isEmpty()) return 0
        var ok = 0
        for ((id, url) in optedIn) {
            if (runBlocking { apiFor(id, url).crashReport(crash) } is ApiResult.Ok) ok++
        }
        return if (ok == 0) null else ok
    }

    companion object {
        const val MAX_TRACE_BYTES = 64 * 1024

        fun crash(appVersion: String, androidVersion: String, deviceModel: String?, stackTrace: String, happenedAt: Instant): CrashReport =
            CrashReport(appVersion, androidVersion, deviceModel, redactTrace(stackTrace), happenedAt)

        /** REQ-OPS-003: redacted, then cut to [MAX_TRACE_BYTES] of UTF-8 (REQ-DEV-057). */
        fun redactTrace(stackTrace: String): String {
            var trace = LogRedactor.redact(stackTrace)
            while (trace.toByteArray().size > MAX_TRACE_BYTES) trace = trace.dropLast(trace.length / 10 + 1)
            return trace
        }
    }
}

/**
 * ACRA's sender, in ACRA's own process: each opted-in Profile's target over HTTP, with that Profile's
 * Device credential (REQ-DEV-057). A Profile not paired yet has none, so its send fails and ACRA keeps
 * the report for a later start; [CrashReportFiles] bounds what is kept.
 */
class CrashSender(private val context: Context) : ReportSender {
    override fun send(context: Context, errorContent: CrashReportData) {
        val prefs = CrashReportPrefs.of(this.context)
        val crash = CrashUploader.crash(
            appVersion = errorContent.getString(ReportField.APP_VERSION_NAME) ?: BuildConfig.VERSION_NAME,
            androidVersion = errorContent.getString(ReportField.ANDROID_VERSION) ?: Build.VERSION.RELEASE,
            deviceModel = errorContent.getString(ReportField.PHONE_MODEL),
            stackTrace = errorContent.getString(ReportField.STACK_TRACE).orEmpty(),
            happenedAt = errorContent.getString(ReportField.USER_CRASH_DATE)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: Instant.now(),
        )
        val credentials = KeystoreCredentialStore.of(this.context)
        val sent = CrashUploader { id, url -> HttpDeviceApi(url, credentials.get(id), AppContainer.USER_AGENT) }.upload(prefs.optedIn(), crash)
        if (sent == null) throw ReportSenderException("no target accepted the crash report")
    }
}

class CrashSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender = CrashSender(context.applicationContext)
}

/**
 * Redacts the stack trace as ACRA collects it, before ACRA stores the report in app storage (T4.6 QA
 * note): a report kept while sending fails is already masked on disk (REQ-OPS-003), not only when it
 * is sent. ACRA calls this after collecting and before saving the report file.
 */
class CrashRedaction : ReportingAdministrator {
    override fun shouldSendReport(context: Context, config: CoreConfiguration, crashReportData: CrashReportData): Boolean {
        crashReportData.getString(ReportField.STACK_TRACE)?.let { crashReportData.put(ReportField.STACK_TRACE, CrashUploader.redactTrace(it)) }
        return true
    }
}

/**
 * Crash reports ACRA keeps while no target accepts them (T4.6 QA note): at most [MAX_FILES], none older
 * than [MAX_AGE] (my call), pruned on every app start. A report that can't reach any target is not
 * worth keeping for long; the local log has the crash too (REQ-OPS-013).
 */
object CrashReportFiles {
    const val MAX_FILES = 5
    val MAX_AGE: Duration = Duration.ofDays(7)

    /** Deletes reports older than [maxAge], then the oldest beyond [maxFiles]. Returns how many it deleted. */
    fun prune(files: List<File>, now: Instant, maxAge: Duration = MAX_AGE, maxFiles: Int = MAX_FILES): Int {
        val cutoff = now.minus(maxAge).toEpochMilli()
        val (old, recent) = files.filter { it.isFile }.partition { it.lastModified() < cutoff }
        val surplus = recent.sortedByDescending { it.lastModified() }.drop(maxFiles)
        return (old + surplus).count { it.delete() }
    }
}
