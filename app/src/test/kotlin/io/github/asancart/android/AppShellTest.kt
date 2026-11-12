package io.github.asancart.android

import android.Manifest
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.asancart.android.ui.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real activity on the JVM. The test JVM runs in English: the app must still be Persian and RTL. */
@RunWith(RobolectricTestRunner::class)
class AppShellTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    @Test
    fun firstLaunchShowsWelcomeThenHomeWithTheDefaultProfile() {
        rule.onNodeWithText(text(R.string.welcome_tagline)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.welcome_start)).performClick()

        // REQ-APP-021: the app never opens empty.
        rule.onNodeWithText(text(R.string.profile_default_name)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.profile_setup_unfinished)).assertIsDisplayed()
        assertTrue((rule.activity.application as AsancartApp).container.settings.firstRunDone.value)
    }

    @Test
    fun layoutIsRightToLeftInPersian() {
        val config = rule.activity.resources.configuration
        assertEquals("fa", config.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.layoutDirection)
        assertTrue(rule.activity.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)
    }

    @Test
    fun themeChoiceIsKept() {
        rule.onNodeWithText(text(R.string.welcome_start)).performClick()
        rule.onNodeWithText(text(R.string.nav_settings)).performClick()
        rule.onNodeWithText(text(R.string.theme_dark)).performClick()

        assertEquals(ThemeChoice.DARK, (rule.activity.application as AsancartApp).container.settings.theme.value)
    }

    @Test
    fun noStartupInitializerReachesPlayServices() {
        // REQ-APP-041: emoji2's initializer would ask the system font provider (Play Services on most phones).
        val provider = rule.activity.packageManager.getProviderInfo(
            ComponentName(rule.activity, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )
        val initializers = provider.metaData?.keySet().orEmpty()
        // This is the real merged manifest: the other library initializers are still in it.
        assertTrue(initializers.toString(), "androidx.lifecycle.ProcessLifecycleInitializer" in initializers)
        assertFalse(initializers.toString(), "androidx.emoji2.text.EmojiCompatInitializer" in initializers)

        // And at runtime, after start-up, EmojiCompat was never configured.
        val emojiCompat = Class.forName("androidx.emoji2.text.EmojiCompat")
        assertFalse(emojiCompat.getMethod("isConfigured").invoke(null) as Boolean)
    }

    @Test
    fun manifestAsksForNoNetworkOrSmsPermissionYet() {
        val info = rule.activity.packageManager.getPackageInfo(rule.activity.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        // BLK-001: no networking. SMS permissions come with capture (T4.2).
        for (p in listOf(Manifest.permission.INTERNET, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) {
            assertFalse("$p must not be requested yet", p in requested)
        }
    }
}
