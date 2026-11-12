package io.github.asancart.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import io.github.asancart.android.core.capture.UnparsedReason
import io.github.asancart.android.core.capture.UnparsedSms
import io.github.asancart.android.ui.MainShell
import io.github.asancart.android.ui.activity.ActivityScreen
import io.github.asancart.android.ui.activity.ActivityTab
import io.github.asancart.android.ui.activity.UnparsedDetailScreen
import io.github.asancart.android.ui.permissions.PermissionStatus
import io.github.asancart.android.ui.permissions.PermissionsScreen
import io.github.asancart.android.ui.Tab
import io.github.asancart.android.ui.theme.AcTheme
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
import java.time.Instant

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

    private val now = Instant.now()
    private val samples = listOf(
        UnparsedSms("u1", "p", "700717", "\u0628\u0627\u0646\u0643 \u0645\u0644\u064A \u0627\u064A\u0631\u0627\u0646\n\u0627\u0646\u062A\u0642\u0627\u0644 \u0627\u0632 6219:5,000,000+\n\u062D\u0633\u0627\u0628:10000\n\u0645\u0627\u0646\u062F\u0647:203,088,329\n0820-09:14", now.minusSeconds(3600), UnparsedReason.NO_TEMPLATE_MATCHED, null, listOf("melli-interest", "melli-account", "melli-otp")),
        UnparsedSms("u2", "p", "Bank Mellat", "\u062D\u0633\u0627\u06288065955489\n\u0648\u0627\u0631\u06CC\u0632 \u0634\u0628\u06272,000,000\n\u0645\u0627\u0646\u062F\u064721,236,833\n05/08/19-18:30", now.minusSeconds(26 * 3600), UnparsedReason.DIRECTION_UNKNOWN, "mellat-account", listOf("mellat-interest", "mellat-account")),
    )

    @Composable
    private fun UnparsedList() = ActivityScreen(ActivityTab.UNPARSED, {}, samples, {}, {})

    @Test fun unparsedListLight() = shoot("unparsed-list-light", dark = false) { UnparsedList() }

    @Test fun unparsedListDark() = shoot("unparsed-list-dark", dark = true) { UnparsedList() }

    @Test fun unparsedDetailLight() = shoot("unparsed-detail-light", dark = false) { UnparsedDetailScreen(samples[0], {}, {}, {}) }

    @Test fun unparsedDetailDark() = shoot("unparsed-detail-dark", dark = true) { UnparsedDetailScreen(samples[1], {}, {}, {}) }

    private val someGranted = PermissionStatus(receiveSms = true, readSms = true, batteryExempt = false, notifications = true, autostartConfirmed = false, smsLocked = false)
    private val locked = someGranted.copy(receiveSms = false, readSms = false, smsLocked = true)

    @Test fun permissionsLight() = shoot("permissions-light", dark = false) { PermissionsScreen(someGranted, {}, {}, {}, {}, {}, {}, {}) }

    @Test fun permissionsLockedDark() = shoot("permissions-locked-dark", dark = true) { PermissionsScreen(locked, {}, {}, {}, {}, {}, {}, {}) }

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val app = container
        rule.setContent {
            CompositionLocalProvider(LocalAppContainer provides app, LocalLayoutDirection provides LayoutDirection.Rtl) {
                // Screens drawn alone get the theme background the app's shell gives them.
                AsancartTheme(dark = dark) { Box(Modifier.fillMaxSize().background(AcTheme.colors.bg)) { content() } }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
