package io.github.asancart.android

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.asancart.android.capture.AndroidInbox
import io.github.asancart.android.capture.CaptureService
import io.github.asancart.android.capture.CaptureStore
import io.github.asancart.android.capture.SmsPermissions
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.StubDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.InMemoryProfileRepository
import io.github.asancart.android.core.profile.ProfileRepository
import io.github.asancart.android.log.AppLog
import io.github.asancart.android.settings.SettingsStore

/** Hand-wired dependencies for the whole app; one instance per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))

    /** The Platform address is a build setting (app/build.gradle.kts), never written in code. */
    val profiles: ProfileRepository = InMemoryProfileRepository(ApiTarget.platform(BuildConfig.PLATFORM_API_BASE_URL))

    val smsPermissions = SmsPermissions(app)

    /** BLK-001 / DEC-030: no networking yet, so the stub is the only Device API (api/device-api.md). */
    val deviceApi: DeviceApi = StubDeviceApi()

    val log: AppLog = AppLog.forBuild(BuildConfig.DEBUG)

    /** SMS capture: receiver, inbox re-scan, unparsed list, outgoing queue (REQ-APP-010..017). */
    val capture = CaptureService(CaptureStore(app), profiles, AndroidInbox(app, smsPermissions), deviceApi, log)

    init {
        // REQ-APP-021: the app never opens empty; the first launch already has its default Profile.
        val profile = profiles.ensureDefaultProfile(app.getString(R.string.profile_default_name))
        log.info("profile_ready", "profile_id" to profile.id)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
