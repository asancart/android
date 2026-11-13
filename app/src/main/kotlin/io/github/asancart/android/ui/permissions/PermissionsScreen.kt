package io.github.asancart.android.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.asancart.android.LocalAppContainer
import io.github.asancart.android.R
import io.github.asancart.android.capture.SmsPermissions
import io.github.asancart.android.settings.SettingsStore
import io.github.asancart.android.ui.components.AcCard
import io.github.asancart.android.ui.components.AcIcon
import io.github.asancart.android.ui.components.AcPill
import io.github.asancart.android.ui.components.BackTopBar
import io.github.asancart.android.ui.components.LeadIcon
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.theme.AcShapes
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.Tone

/** What the Permissions screen shows (REQ-APP-026). */
data class PermissionStatus(
    val receiveSms: Boolean,
    val readSms: Boolean,
    val batteryExempt: Boolean,
    val notifications: Boolean,
    val autostartConfirmed: Boolean,
    val smsLocked: Boolean,
) {
    /** Required: receive SMS, read SMS, battery optimization exemption. */
    val requiredMissing: Int get() = listOf(receiveSms, readSms, batteryExempt).count { !it }

    companion object {
        fun read(context: Context, settings: SettingsStore): PermissionStatus {
            fun has(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            val power = context.getSystemService(PowerManager::class.java)
            val notifications = context.getSystemService(NotificationManager::class.java)
            val receive = has(Manifest.permission.RECEIVE_SMS)
            val read = has(Manifest.permission.READ_SMS)
            if (receive && read && settings.smsPermissionLocked) settings.smsPermissionLocked = false
            return PermissionStatus(
                receiveSms = receive,
                readSms = read,
                batteryExempt = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
                notifications = notifications?.areNotificationsEnabled() == true,
                autostartConfirmed = settings.autostartConfirmed,
                smsLocked = settings.smsPermissionLocked && !(receive && read),
            )
        }
    }
}

/** Permissions screen with its system prompts and settings pages (design screen 8). */
@Composable
fun PermissionsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppContainer.current.settings
    var status by remember { mutableStateOf(PermissionStatus.read(context, settings)) }
    fun refresh() {
        status = PermissionStatus.read(context, settings)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        val activity = context.findActivity()
        // Denied without a dialog: a restricted setting, or denied for good (REQ-APP-026).
        val noDialog = activity != null && SmsPermissions.REQUIRED.none { activity.shouldShowRequestPermissionRationale(it) }
        settings.smsPermissionLocked = !granted && noDialog
        refresh()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    PermissionsScreen(
        status = status,
        onBack = onBack,
        onRequestSms = { if (status.smsLocked) context.openAppInfo() else smsLauncher.launch(SmsPermissions.REQUIRED.toTypedArray()) },
        onOpenAppInfo = { context.openAppInfo() },
        onBattery = { context.requestBatteryExemption() },
        onNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.openNotificationSettings()
        },
        onAutostartOpen = { context.openAppInfo() },
        onAutostartConfirm = {
            settings.autostartConfirmed = true
            refresh()
        },
    )
}

@Composable
fun PermissionsScreen(
    status: PermissionStatus,
    onBack: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onBattery: () -> Unit,
    onNotifications: () -> Unit,
    onAutostartOpen: () -> Unit,
    onAutostartConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.permissions_title), onBack)
        ScreenBody {
            Text(stringResource(R.string.permissions_intro), style = AcTheme.type.small, color = AcTheme.colors.muted)
            SmsRow(R.drawable.ic_sms, R.string.perm_receive, R.string.perm_receive_why, status.receiveSms, status.smsLocked, onRequestSms, onOpenAppInfo)
            SmsRow(R.drawable.ic_inbox, R.string.perm_read, R.string.perm_read_why, status.readSms, status.smsLocked, onRequestSms, onOpenAppInfo)
            PermissionRow(
                icon = R.drawable.ic_battery,
                title = R.string.perm_battery,
                why = R.string.perm_battery_why,
                granted = status.batteryExempt,
                pending = Pending.REQUIRED,
                actions = { RowButton(R.string.perm_open_settings, primary = true, onClick = onBattery) },
            )
            PermissionRow(
                icon = R.drawable.ic_bell,
                title = R.string.perm_notif,
                why = R.string.perm_notif_why,
                granted = status.notifications,
                pending = Pending.RECOMMENDED,
                actions = { RowButton(R.string.perm_allow, primary = false, onClick = onNotifications) },
            )
            PermissionRow(
                icon = R.drawable.ic_power,
                title = R.string.perm_autostart,
                why = R.string.perm_autostart_why,
                granted = status.autostartConfirmed,
                pending = Pending.UNCHECKED,
                actions = {
                    RowButton(R.string.perm_open_settings, primary = false, onClick = onAutostartOpen)
                    RowButton(R.string.perm_autostart_done, primary = false, onClick = onAutostartConfirm)
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AcIcon(R.drawable.ic_camera, tint = AcTheme.colors.muted, size = 16.dp)
                Text(stringResource(R.string.perm_camera_note), style = AcTheme.type.caption, color = AcTheme.colors.muted)
            }
        }
    }
}

private enum class Pending { REQUIRED, RECOMMENDED, UNCHECKED }

@Composable
private fun SmsRow(@DrawableRes icon: Int, @StringRes title: Int, @StringRes why: Int, granted: Boolean, locked: Boolean, onRequest: () -> Unit, onAppInfo: () -> Unit) {
    if (!granted && locked) {
        AcCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeadIcon(R.drawable.ic_lock, Tone.BAD)
                Text(stringResource(title), style = AcTheme.type.title, modifier = Modifier.weight(1f))
                AcPill(stringResource(R.string.perm_locked), Tone.BAD)
            }
            Text(stringResource(R.string.perm_sms_locked), style = AcTheme.type.small, color = AcTheme.colors.muted)
            Row { RowButton(R.string.perm_open_app_info, primary = true, onClick = onAppInfo) }
        }
    } else {
        PermissionRow(icon, title, why, granted, Pending.REQUIRED) { RowButton(R.string.perm_allow, primary = true, onClick = onRequest) }
    }
}

@Composable
private fun PermissionRow(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes why: Int,
    granted: Boolean,
    pending: Pending,
    actions: @Composable () -> Unit,
) {
    val tone = when {
        granted -> Tone.OK
        pending == Pending.REQUIRED -> Tone.BAD
        else -> Tone.WARN
    }
    val pill = when {
        granted -> R.string.perm_granted
        pending == Pending.REQUIRED -> R.string.perm_required
        pending == Pending.RECOMMENDED -> R.string.perm_recommended
        else -> R.string.perm_unchecked
    }
    AcCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeadIcon(icon, tone)
            Text(stringResource(title), style = AcTheme.type.title, modifier = Modifier.weight(1f))
            AcPill(stringResource(pill), tone)
        }
        Text(stringResource(why), style = AcTheme.type.small, color = AcTheme.colors.muted)
        if (!granted) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun RowButton(@StringRes text: Int, primary: Boolean, onClick: () -> Unit) {
    val c = AcTheme.colors
    Button(
        onClick = onClick,
        shape = AcShapes.pill,
        modifier = Modifier.heightIn(min = 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) c.primary else c.surface2,
            contentColor = if (primary) c.onPrimary else c.ink,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(stringResource(text), style = AcTheme.type.button.copy(fontSize = AcTheme.type.small.fontSize))
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun Context.openAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * REQ-APP-026 requires the exemption: without it Android stops the app and SMS are missed. The
 * app is not distributed on Google Play, whose policy limits this request.
 */
@SuppressLint("BatteryLife")
private fun Context.requestBatteryExemption() {
    try {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some ROMs have no such dialog: the battery optimization list is the fallback.
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
