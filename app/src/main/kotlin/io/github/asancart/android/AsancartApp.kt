package io.github.asancart.android

import android.app.Application
import android.content.Context
import io.github.asancart.android.log.CrashSenderFactory
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.plugins.SimplePluginLoader

class AsancartApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // REQ-OPS-013: ACRA, no Google services. Only these fields are collected: no logcat, no
        // preferences, no device ids. CrashSender sends to opted-in Profiles only.
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
                .withPluginLoader(SimplePluginLoader(CrashSenderFactory::class.java))
                .withSendReportsInDevMode(true)
                .withAlsoReportToAndroidFramework(true),
            false,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // ACRA's sender process needs nothing of the app.
        if (ACRA.isACRASenderServiceProcess()) return
        container = AppContainer(this)
        // The crash goes to the local log first (REQ-OPS-013), then to ACRA.
        val acra = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            container.fileLog.errorNow("crash", error, "thread" to thread.name)
            acra?.uncaughtException(thread, error)
        }
        // REQ-APP-011: every start re-scans the inbox from the last checkpoint.
        container.capture.start()
    }
}
