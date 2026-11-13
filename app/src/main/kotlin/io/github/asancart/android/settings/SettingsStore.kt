package io.github.asancart.android.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.asancart.android.ui.theme.ThemeChoice

/** App-wide settings kept on the phone: theme (REQ-BRAND-005) and whether Welcome was passed. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val themeState = MutableStateFlow(readTheme())
    private val firstRunState = MutableStateFlow(prefs.getBoolean(KEY_FIRST_RUN_DONE, false))

    val theme: StateFlow<ThemeChoice> = themeState.asStateFlow()

    /** Welcome is shown on first launch only. */
    val firstRunDone: StateFlow<Boolean> = firstRunState.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        prefs.edit().putString(KEY_THEME, choice.name).apply()
        themeState.value = choice
    }

    /**
     * REQ-APP-026: an SMS permission request ended denied without a dialog (a restricted setting,
     * or denied for good). The Permissions screen then sends the user to the app's system page.
     */
    var smsPermissionLocked: Boolean
        get() = prefs.getBoolean(KEY_SMS_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMS_LOCKED, value).apply()

    /** Vendor autostart can't be read; the user confirms it (REQ-APP-026). */
    var autostartConfirmed: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()

    fun completeFirstRun() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply()
        firstRunState.value = true
    }

    private fun readTheme(): ThemeChoice =
        prefs.getString(KEY_THEME, null)?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.SYSTEM

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FIRST_RUN_DONE = "first_run_done"
        const val KEY_SMS_LOCKED = "sms_permission_locked"
        const val KEY_AUTOSTART = "autostart_confirmed"
    }
}
