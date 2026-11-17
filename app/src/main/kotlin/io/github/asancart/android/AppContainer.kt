package io.github.asancart.android

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.asancart.android.capture.AndroidInbox
import io.github.asancart.android.capture.CaptureService
import io.github.asancart.android.capture.CaptureStore
import io.github.asancart.android.capture.SmsPermissions
import io.github.asancart.android.core.deviceapi.DeviceApi
import io.github.asancart.android.core.deviceapi.HttpDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.CredentialStore
import io.github.asancart.android.core.profile.JsonFileProfileRepository
import io.github.asancart.android.core.profile.ProfileConnections
import io.github.asancart.android.core.profile.ProfileRepository
import io.github.asancart.android.log.AppLog
import io.github.asancart.android.log.CrashReportPrefs
import io.github.asancart.android.log.FileLog
import io.github.asancart.android.profile.KeystoreCredentialStore
import io.github.asancart.android.settings.SettingsStore
import java.io.File

/**
 * Hand-wired dependencies for the whole app; one instance per process. [deviceApiFor] gives a
 * target's Device API with a credential or none; the app uses [HttpDeviceApi], tests a stand-in.
 */
class AppContainer(
    context: Context,
    deviceApiFor: ((ApiTarget, String?) -> DeviceApi)? = null,
    credentials: CredentialStore? = null,
) {
    private val app = context.applicationContext

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))

    /** REQ-OPS-010: 5 × 1 MB in app-private storage. */
    val fileLog = FileLog(File(app.filesDir, "logs"), env = if (BuildConfig.DEBUG) "debug" else "release")

    val log: AppLog = AppLog.app(fileLog, BuildConfig.DEBUG)

    /** The Platform address is a build setting (app/build.gradle.kts, REQ-APP-022), never written in code. */
    val platform = ApiTarget.platform(BuildConfig.PLATFORM_API_BASE_URL)

    /** Profiles on disk (REQ-APP-020); the Platform target always takes this build's address. */
    val profiles: ProfileRepository = JsonFileProfileRepository(
        File(app.filesDir, "profiles.json"),
        platform,
        onUnreadable = { e -> log.error("profiles_unreadable", e) },
    )

    /** Device credentials, encrypted with a Keystore key (T4.3). */
    val credentials: CredentialStore = credentials ?: KeystoreCredentialStore.of(app)

    val deviceApiFor: (ApiTarget, String?) -> DeviceApi = deviceApiFor ?: { target, credential -> HttpDeviceApi(target.baseUrl, credential, USER_AGENT) }

    val smsPermissions = SmsPermissions(app)

    /** REQ-OPS-013 opt-ins. */
    val crashReports = CrashReportPrefs.of(app)

    /**
     * SMS capture: receiver, inbox re-scan, unparsed list, outgoing queue (REQ-APP-010..017). The
     * official Parser Templates come from the Platform (REQ-DEV-004, no credential).
     */
    val capture = CaptureService(CaptureStore(app), profiles, AndroidInbox(app, smsPermissions), this.deviceApiFor(platform, null), log)

    /** Pair, Cards refresh, revoked state and removal with Unpair (T4.3). */
    val connections = ProfileConnections(
        profiles = profiles,
        credentials = this.credentials,
        apiFor = this.deviceApiFor,
        appVersion = BuildConfig.VERSION_NAME,
        defaultProfileName = { app.getString(R.string.profile_default_name) },
        onRemoved = { id ->
            capture.forgetProfile(id)
            crashReports.forget(id)
        },
        events = { name, fields -> log.info(name, *fields) },
    )

    init {
        // REQ-APP-021: the app never opens empty; the first launch already has its default Profile.
        val profile = profiles.ensureDefaultProfile(app.getString(R.string.profile_default_name))
        log.info("profile_ready", "profile_id" to profile.id)
    }

    companion object {
        /** Sent with every Device API call. */
        const val USER_AGENT = "Asancart-Android/" + BuildConfig.VERSION_NAME
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
