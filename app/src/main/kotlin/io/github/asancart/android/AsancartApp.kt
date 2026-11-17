package io.github.asancart.android

import android.app.Application
import android.content.Context
import io.github.asancart.android.log.CrashRedaction
import io.github.asancart.android.log.CrashReportFiles
import io.github.asancart.android.log.CrashSenderFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.file.ReportLocator
import org.acra.plugins.SimplePluginLoader
import java.time.Instant

open class AsancartApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that outlives a screen: the Cards refresh on start (REQ-APP-040). Failures are logged. */
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> container.log.error("app_job_failed", e) })
    }

    /** The app's dependencies; tests replace the Device API here. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // REQ-OPS-013: ACRA, no Google services. Only these fields are collected: no logcat, no
        // preferences, no device ids. CrashRedaction masks the trace before ACRA stores it;
        // CrashSender sends to opted-in Profiles only. The Android framework's own crash output
        // (an unmasked trace in logcat) only in debug builds (T4.6 QA note, my call).
        ACRA.init(
            this,
            CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
                .withReportFormat(StringFormat.JSON)
                .withReportContent(
                    ReportField.REPORT_ID,
                    ReportField.APP_VERSION_NAME,
                    ReportField.ANDROID_VERSION,
                    ReportField.PHONE_MODEL,
                    ReportField.STACK_TRACE,
                    ReportField.USER_CRASH_DATE,
                )
                .withPluginLoader(SimplePluginLoader(CrashSenderFactory::class.java, CrashRedaction::class.java))
                .withSendReportsInDevMode(true)
                .withAlsoReportToAndroidFramework(BuildConfig.DEBUG),
            false,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // ACRA's sender process needs nothing of the app.
        if (ACRA.isACRASenderServiceProcess()) return
        container = createContainer()
        // The crash goes to the local log first (REQ-OPS-013), then to ACRA.
        val acra = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            container.fileLog.errorNow("crash", error, "thread" to thread.name)
            acra?.uncaughtException(thread, error)
        }
        // REQ-APP-011: every start re-scans the inbox from the last checkpoint.
        container.capture.start()
        appScope.launch {
            // Crash reports no target took stay bounded (T4.6 QA note).
            val stored = ReportLocator(this@AsancartApp)
            val pruned = CrashReportFiles.prune(stored.approvedReports.toList() + stored.unapprovedReports.toList(), Instant.now())
            if (pruned > 0) container.log.info("crash_reports_pruned", "files" to pruned)
            // A removal cut short leaves no credential behind; then REQ-APP-040's Cards refresh.
            container.connections.dropOrphanCredentials()
            container.connections.refreshAllCards()
        }
    }
}
