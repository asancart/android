package org.asancart.android

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import org.asancart.android.capture.SmsPermissions
import org.asancart.android.core.deviceapi.DeviceApi
import org.asancart.android.core.deviceapi.StubDeviceApi
import org.asancart.android.core.profile.InMemoryProfileRepository
import org.asancart.android.core.profile.ProfileRepository
import org.asancart.android.log.AppLog
import org.asancart.android.settings.SettingsStore

/** Hand-wired dependencies for the whole app; one instance per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))

    val profiles: ProfileRepository = InMemoryProfileRepository()

    val smsPermissions = SmsPermissions(app)

    /** BLK-001 / DEC-030: no networking yet, so the stub is the only Device API (api/device-api.md). */
    val deviceApi: DeviceApi = StubDeviceApi()

    val log: AppLog = AppLog.forBuild(BuildConfig.DEBUG)

    init {
        // REQ-APP-021: the app never opens empty; the first launch already has its default Profile.
        val profile = profiles.ensureDefaultProfile(app.getString(R.string.profile_default_name))
        log.info("profile_ready", "profile_id" to profile.id)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
