package io.github.asancart.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.LocalAppContainer
import io.github.asancart.android.R
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.ui.activity.ActivityScreen
import io.github.asancart.android.ui.components.BottomNav
import io.github.asancart.android.ui.components.NavItem
import io.github.asancart.android.ui.home.HomeRoute
import io.github.asancart.android.ui.logs.LogsRoute
import io.github.asancart.android.ui.settings.SettingsScreen
import io.github.asancart.android.ui.templates.TemplatesScreen
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.welcome.WelcomeScreen

/** The four tabs of daily use (design C8). */
enum class Tab { HOME, ACTIVITY, TEMPLATES, SETTINGS }

/** App root: Welcome on first launch, then the tab shell. The UI is Persian and right-to-left. */
@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val firstRunDone by container.settings.firstRunDone.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (firstRunDone) MainShell() else WelcomeScreen(versionName = BuildConfig.VERSION_NAME, onStart = container.settings::completeFirstRun)
    }
}

@Composable
fun MainShell(initialTab: Tab = Tab.HOME) {
    val container = LocalAppContainer.current
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var logsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = logsOpen) { logsOpen = false }
    BackHandler(enabled = !logsOpen && tab != Tab.HOME) { tab = Tab.HOME }

    CrashReportsQuestion()

    val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    if (logsOpen) {
        Box(Modifier.fillMaxSize().background(AcTheme.colors.bg).then(insets).windowInsetsPadding(WindowInsets.navigationBars)) {
            LogsRoute(onBack = { logsOpen = false })
        }
        return
    }
    Column(Modifier.fillMaxSize().background(AcTheme.colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().then(insets)) {
            when (tab) {
                Tab.HOME -> HomeRoute()
                Tab.ACTIVITY -> ActivityScreen()
                Tab.TEMPLATES -> TemplatesScreen()
                Tab.SETTINGS -> {
                    val theme by container.settings.theme.collectAsStateWithLifecycle()
                    SettingsScreen(theme = theme, onTheme = container.settings::setTheme, versionName = BuildConfig.VERSION_NAME, onOpenLogs = { logsOpen = true })
                }
            }
        }
        BottomNav(
            items = listOf(
                NavItem(Tab.HOME, stringResource(R.string.nav_home), R.drawable.ic_home),
                NavItem(Tab.ACTIVITY, stringResource(R.string.nav_activity), R.drawable.ic_activity),
                NavItem(Tab.TEMPLATES, stringResource(R.string.nav_templates), R.drawable.ic_doc),
                NavItem(Tab.SETTINGS, stringResource(R.string.nav_settings), R.drawable.ic_sliders),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
    }
}

/**
 * REQ-OPS-013: crash reports are off until the user says yes; for the Asancart Profile the app
 * asks once (my call in the docs). Custom targets are only switched on in Settings → Logs.
 */
@Composable
private fun CrashReportsQuestion() {
    val container = LocalAppContainer.current
    var show by remember { mutableStateOf(!container.crashReports.asked) }
    if (!show) return
    val platform = container.profiles.profiles.value.firstOrNull { it.target.kind == ApiTarget.Kind.PLATFORM }
    if (platform == null) return
    fun answer(yes: Boolean) {
        container.crashReports.setOptIn(platform.id, platform.target.baseUrl, yes)
        container.log.info("crash_reports_setting", "profile_id" to platform.id, "on" to yes)
        show = false
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.crash_prompt_title), style = AcTheme.type.title) },
        text = { Text(stringResource(R.string.crash_prompt_body), style = AcTheme.type.small) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.crash_prompt_yes), color = AcTheme.colors.primaryText) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.crash_prompt_no), color = AcTheme.colors.muted) } },
        containerColor = AcTheme.colors.surface,
    )
}
