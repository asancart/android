package io.github.asancart.android

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.asancart.android.capture.SmsPermissions
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.InMemoryProfileRepository
import io.github.asancart.android.core.profile.ProfileRepository
import io.github.asancart.android.log.AppLog
import io.github.asancart.android.log.CrashReportPrefs
import io.github.asancart.android.log.FileLog
import io.github.asancart.android.settings.SettingsStore
import java.io.File

/** Hand-wired dependencies for the whole app; one instance per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))

    /**
     * The Platform address is a build setting (app/build.gradle.kts), never written in code. The
     * default Profile's id is fixed, so settings kept per Profile (crash reports) survive restarts
     * until Profiles are stored (T4.3).
     */
    val profiles: ProfileRepository = InMemoryProfileRepository(ApiTarget.platform(BuildConfig.PLATFORM_API_BASE_URL), newId = { DEFAULT_PROFILE_ID })

    val smsPermissions = SmsPermissions(app)

    /** The stub until the HTTP adapter comes with pairing (T4.3); api/device-api.md. */
    val deviceApi: DeviceApi = StubDeviceApi()

    /** REQ-OPS-010: 5 × 1 MB in app-private storage. */
    val fileLog = FileLog(File(app.filesDir, "logs"), env = if (BuildConfig.DEBUG) "debug" else "release")

    val log: AppLog = AppLog.app(fileLog, BuildConfig.DEBUG)

    /** REQ-OPS-013 opt-ins. */
    val crashReports = CrashReportPrefs.of(app)

    init {
        // REQ-APP-021: the app never opens empty; the first launch already has its default Profile.
        val profile = profiles.ensureDefaultProfile(app.getString(R.string.profile_default_name))
        log.info("profile_ready", "profile_id" to profile.id)
    }
}

const val DEFAULT_PROFILE_ID = "default"

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
