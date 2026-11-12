package org.asancart.android

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import org.asancart.android.ui.MainShell
import org.asancart.android.ui.Tab
import org.asancart.android.ui.theme.AsancartTheme
import org.asancart.android.ui.welcome.WelcomeScreen
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

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val app = container
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
