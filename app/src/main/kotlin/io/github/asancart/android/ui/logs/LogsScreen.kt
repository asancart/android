package io.github.asancart.android.ui.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.LocalAppContainer
import io.github.asancart.android.R
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.log.LogExport
import io.github.asancart.android.ui.components.AcCard
import io.github.asancart.android.ui.components.AcSwitch
import io.github.asancart.android.ui.components.BackTopBar
import io.github.asancart.android.ui.components.ListGroup
import io.github.asancart.android.ui.components.PrimaryButton
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.SectionTitle
import io.github.asancart.android.ui.theme.AcShapes
import io.github.asancart.android.ui.theme.AcTheme
import java.io.File

/** Settings → Logs (REQ-OPS-012, REQ-OPS-013): crash reports per Profile, and the log export. */
@Composable
fun LogsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val profiles by container.profiles.profiles.collectAsStateWithLifecycle()
    var optedIn by remember { mutableStateOf(container.crashReports.optedIn().keys) }
    val lines = remember { LogExport.tail(container.fileLog, 60) }
    LogsScreen(
        profiles = profiles,
        optedIn = optedIn,
        lines = lines,
        onBack = onBack,
        onCrashReports = { profile, on ->
            container.crashReports.setOptIn(profile.id, profile.target.baseUrl, on)
            container.log.info("crash_reports_setting", "profile_id" to profile.id, "on" to on)
            optedIn = container.crashReports.optedIn().keys
        },
        onShare = {
            val file = LogExport.write(container.fileLog, File(context.cacheDir, "exports"), BuildConfig.VERSION_NAME)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".logs", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.logs_share_title)))
            container.log.info("log_export_shared", "bytes" to file.length())
        },
    )
}

@Composable
fun LogsScreen(
    profiles: List<Profile>,
    optedIn: Set<String>,
    lines: List<String>,
    onBack: () -> Unit,
    onCrashReports: (Profile, Boolean) -> Unit,
    onShare: () -> Unit,
) {
    val c = AcTheme.colors
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.logs_title), onBack)
        ScreenBody {
            SectionTitle(stringResource(R.string.logs_crash_section))
            Text(stringResource(R.string.logs_crash_about), style = AcTheme.type.small, color = c.muted)
            ListGroup {
                profiles.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = c.line)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.logs_crash_switch, p.name), style = AcTheme.type.bodyMedium)
                            Text("\u2066${p.target.host}\u2069", style = AcTheme.type.mono, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val on = p.id in optedIn
                        AcSwitch(checked = on, onCheckedChange = { onCrashReports(p, it) }, enabled = true, description = stringResource(R.string.logs_crash_switch, p.name))
                    }
                }
            }

            SectionTitle(stringResource(R.string.logs_file_section))
            Text(stringResource(R.string.logs_file_about), style = AcTheme.type.small, color = c.muted)
            PrimaryButton(stringResource(R.string.logs_share), onClick = onShare)
            AcCard(flat = true) {
                if (lines.isEmpty()) {
                    Text(stringResource(R.string.logs_empty), style = AcTheme.type.small, color = c.muted)
                } else {
                    // Log lines are Latin JSON: left to right.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AcShapes.tile)
                                .background(c.surface)
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (line in lines) Text(line, style = AcTheme.type.mono.copy(fontSize = AcTheme.type.caption.fontSize), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
