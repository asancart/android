package org.asancart.android.ui.welcome

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.asancart.android.R
import org.asancart.android.ui.components.LeadIcon
import org.asancart.android.ui.components.PrimaryButton
import org.asancart.android.ui.format.PersianFormat
import org.asancart.android.ui.theme.AcTheme
import org.asancart.android.ui.theme.AsancartMark
import org.asancart.android.ui.theme.Tone

/**
 * First launch only (design screen 1). Says what leaves the phone before asking for anything
 * (DEC-003, DEC-008, DEC-010). The default Profile already exists (REQ-APP-021); Start opens Home.
 * Profile setup (screen 2 onwards) comes with pairing in T4.3.
 */
@Composable
fun WelcomeScreen(versionName: String, onStart: () -> Unit) {
    val c = AcTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Centred when it fits, scrolls on short screens or large text.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsancartMark(modifier = Modifier.size(120.dp), contentDescription = stringResource(R.string.app_name))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.welcome_title), style = AcTheme.type.display, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.welcome_tagline), color = c.muted, textAlign = TextAlign.Center)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Point(R.drawable.ic_shield, stringResource(R.string.welcome_privacy_title), stringResource(R.string.welcome_privacy_body))
                    Point(R.drawable.ic_code, stringResource(R.string.welcome_open_title), stringResource(R.string.welcome_open_body))
                    Point(R.drawable.ic_link, stringResource(R.string.welcome_targets_title), stringResource(R.string.welcome_targets_body))
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(stringResource(R.string.welcome_start), onClick = onStart)
            Text(
                stringResource(R.string.welcome_version, PersianFormat.version(versionName), stringResource(R.string.license_name)),
                style = AcTheme.type.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Point(@DrawableRes icon: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        LeadIcon(icon, Tone.PRIMARY)
        Column(Modifier.weight(1f)) {
            Text(title, style = AcTheme.type.title)
            Text(body, style = AcTheme.type.small, color = AcTheme.colors.muted)
        }
    }
}
