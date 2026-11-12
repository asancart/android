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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.LocalAppContainer
import io.github.asancart.android.R
import io.github.asancart.android.ui.activity.ActivityScreen
import io.github.asancart.android.ui.activity.ActivityTab
import io.github.asancart.android.ui.activity.UnparsedDetailScreen
import io.github.asancart.android.ui.components.BottomNav
import io.github.asancart.android.ui.components.NavItem
import io.github.asancart.android.ui.home.HomeRoute
import io.github.asancart.android.ui.permissions.PermissionStatus
import io.github.asancart.android.ui.permissions.PermissionsRoute
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

/** A screen over the tabs, without the bottom navigation. */
private sealed interface Overlay {
    data object Permissions : Overlay

    data class Unparsed(val id: String) : Overlay

    companion object {
        fun save(o: Overlay?): String? = when (o) {
            null -> null
            Permissions -> "permissions"
            is Unparsed -> "unparsed:" + o.id
        }

        fun restore(s: String?): Overlay? = when {
            s == null -> null
            s == "permissions" -> Permissions
            s.startsWith("unparsed:") -> Unparsed(s.removePrefix("unparsed:"))
            else -> null
        }
    }
}

@Composable
fun MainShell(initialTab: Tab = Tab.HOME, initialActivityTab: ActivityTab = ActivityTab.SENT) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var activityTab by rememberSaveable { mutableStateOf(initialActivityTab) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = Overlay.restore(overlayKey)
    val unparsed by container.capture.unparsed.collectAsStateWithLifecycle()
    var missingPermissions by remember { mutableStateOf(PermissionStatus.read(context, container.settings).requiredMissing) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missingPermissions = PermissionStatus.read(context, container.settings).requiredMissing }

    BackHandler(enabled = overlay != null) { overlayKey = null }
    BackHandler(enabled = overlay == null && tab != Tab.HOME) { tab = Tab.HOME }

    val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    if (overlay != null) {
        Box(Modifier.fillMaxSize().background(AcTheme.colors.bg).then(insets).windowInsetsPadding(WindowInsets.navigationBars)) {
            when (overlay) {
                Overlay.Permissions -> PermissionsRoute(onBack = { overlayKey = null })
                is Overlay.Unparsed -> {
                    val entry = unparsed.firstOrNull { it.id == overlay.id }
                    // Re-read or dismiss removed it: back to the list.
                    LaunchedEffect(entry == null) { if (entry == null) overlayKey = null }
                    if (entry != null) {
                        UnparsedDetailScreen(
                            u = entry,
                            onBack = { overlayKey = null },
                            onReread = { container.capture.reread(entry.id) },
                            onDismiss = { container.capture.dismiss(entry.id) },
                        )
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(AcTheme.colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().then(insets)) {
            when (tab) {
                Tab.HOME -> HomeRoute(onOpenUnparsed = {
                    activityTab = ActivityTab.UNPARSED
                    tab = Tab.ACTIVITY
                })
                Tab.ACTIVITY -> ActivityScreen(
                    tab = activityTab,
                    onTab = { activityTab = it },
                    unparsed = unparsed,
                    onOpenUnparsed = { overlayKey = Overlay.save(Overlay.Unparsed(it)) },
                    onRereadAll = { container.capture.reread() },
                )
                Tab.TEMPLATES -> TemplatesScreen()
                Tab.SETTINGS -> {
                    val theme by container.settings.theme.collectAsStateWithLifecycle()
                    SettingsScreen(
                        theme = theme,
                        onTheme = container.settings::setTheme,
                        versionName = BuildConfig.VERSION_NAME,
                        missingPermissions = missingPermissions,
                        onOpenPermissions = { overlayKey = Overlay.save(Overlay.Permissions) },
                    )
                }
            }
        }
        BottomNav(
            items = listOf(
                NavItem(Tab.HOME, stringResource(R.string.nav_home), R.drawable.ic_home),
                NavItem(Tab.ACTIVITY, stringResource(R.string.nav_activity), R.drawable.ic_activity, badge = unparsed.size),
                NavItem(Tab.TEMPLATES, stringResource(R.string.nav_templates), R.drawable.ic_doc),
                NavItem(Tab.SETTINGS, stringResource(R.string.nav_settings), R.drawable.ic_sliders),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
    }
}
