package io.github.asancart.android

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import io.github.asancart.android.ui.MainShell
import io.github.asancart.android.ui.logs.LogsScreen
import io.github.asancart.android.ui.Tab
import io.github.asancart.android.ui.theme.AsancartTheme
import io.github.asancart.android.ui.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the scaffold screens in light and dark, Persian RTL, at 360 x 760 dp, into
 * app/build/outputs/screenshots for comparison with design/mockups.html. CI uploads them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fa-rIR-ldrtl-w360dp-h760dp-port-xhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val container get() = (RuntimeEnvironment.getApplication() as AsancartApp).container

    @Test fun welcomeLight() = shoot("welcome-light", dark = false) { WelcomeScreen(versionName = BuildConfig.VERSION_NAME, onStart = {}) }

    @Test fun welcomeDark() = shoot("welcome-dark", dark = true) { WelcomeScreen(versionName = BuildConfig.VERSION_NAME, onStart = {}) }

    @Test fun homeLight() = shoot("home-light", dark = false) { MainShell(Tab.HOME) }

    @Test fun homeDark() = shoot("home-dark", dark = true) { MainShell(Tab.HOME) }

    @Test fun activityLight() = shoot("activity-light", dark = false) { MainShell(Tab.ACTIVITY) }

    @Test fun activityDark() = shoot("activity-dark", dark = true) { MainShell(Tab.ACTIVITY) }

    @Test fun templatesLight() = shoot("templates-light", dark = false) { MainShell(Tab.TEMPLATES) }

    @Test fun templatesDark() = shoot("templates-dark", dark = true) { MainShell(Tab.TEMPLATES) }

    @Test fun settingsLight() = shoot("settings-light", dark = false) { MainShell(Tab.SETTINGS) }

    @Test fun settingsDark() = shoot("settings-dark", dark = true) { MainShell(Tab.SETTINGS) }

    @Test fun logsLight() = shoot("logs-light", dark = false) { SampleLogs() }

    @Test fun logsDark() = shoot("logs-dark", dark = true) { SampleLogs() }

    @Composable
    private fun SampleLogs() = LogsScreen(
        profiles = container.profiles.profiles.value,
        optedIn = setOf(DEFAULT_PROFILE_ID),
        lines = listOf(
            "{\"time\":\"2026-11-12T10:00:00Z\",\"level\":\"INFO\",\"app\":\"android\",\"env\":\"debug\",\"msg\":\"profile_ready\",\"profile_id\":\"default\"}",
            "{\"time\":\"2026-11-12T10:00:05Z\",\"level\":\"WARN\",\"app\":\"android\",\"env\":\"debug\",\"msg\":\"templates_refresh_failed\",\"http\":\"503\"}",
        ),
        onBack = {},
        onCrashReports = { _, _ -> },
        onShare = {},
    )

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val app = container
        // The one-time crash-report question is a dialog: answered here so the screen is alone.
        app.crashReports.markAsked()
        rule.setContent {
            CompositionLocalProvider(LocalAppContainer provides app, LocalLayoutDirection provides LayoutDirection.Rtl) {
                AsancartTheme(dark = dark, content = content)
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
