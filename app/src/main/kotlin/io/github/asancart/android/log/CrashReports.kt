package io.github.asancart.android.log

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.CrashReport
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import kotlinx.coroutines.runBlocking
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderException
import org.acra.sender.ReportSenderFactory
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Who gets crash reports (REQ-OPS-013): only Profiles whose user turned it on. Default off for
 * custom targets; the Asancart Profile is asked once (my call in the docs). Kept in its own
 * preferences file, because ACRA sends from its own process.
 */
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

        fun crash(appVersion: String, androidVersion: String, deviceModel: String?, stackTrace: String, happenedAt: Instant): CrashReport {
            var trace = LogRedactor.redact(stackTrace)
            while (trace.toByteArray().size > MAX_TRACE_BYTES) trace = trace.dropLast(trace.length / 10 + 1)
            return CrashReport(appVersion, androidVersion, deviceModel, trace, happenedAt)
        }
    }
}

/** ACRA's sender, in ACRA's own process. Device API: the stub until the HTTP adapter lands. */
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
        val sent = CrashUploader { _, _ -> StubDeviceApi() }.upload(prefs.optedIn(), crash)
        if (sent == null) throw ReportSenderException("no target accepted the crash report")
    }
}

class CrashSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender = CrashSender(context.applicationContext)
}
