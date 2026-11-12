package org.asancart.android.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.asancart.android.R
import org.asancart.android.ui.components.AcCard
import org.asancart.android.ui.components.BigTopBar
import org.asancart.android.ui.components.Choice
import org.asancart.android.ui.components.LeadIcon
import org.asancart.android.ui.components.ListGroup
import org.asancart.android.ui.components.ListRow
import org.asancart.android.ui.components.ScreenBody
import org.asancart.android.ui.components.SectionTitle
import org.asancart.android.ui.components.SegmentedChoice
import org.asancart.android.ui.format.PersianFormat
import org.asancart.android.ui.theme.AcTheme
import org.asancart.android.ui.theme.ThemeChoice
import org.asancart.android.ui.theme.Tone

/**
 * Settings (design screen 16). Not yet here: the permissions row (T4.2), automatic template
 * updates (T4.2) and Settings -> Logs (REQ-OPS-012, T4.6).
 */
@Composable
fun SettingsScreen(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit, versionName: String) {
    val c = AcTheme.colors
    Column(Modifier.fillMaxSize()) {
        BigTopBar(stringResource(R.string.settings_title))
        ScreenBody {
            SectionTitle(stringResource(R.string.settings_section_appearance))
            SegmentedChoice(
                choices = listOf(
                    Choice(ThemeChoice.LIGHT, stringResource(R.string.theme_light), R.drawable.ic_sun),
                    Choice(ThemeChoice.DARK, stringResource(R.string.theme_dark), R.drawable.ic_moon),
                    Choice(ThemeChoice.SYSTEM, stringResource(R.string.theme_system), R.drawable.ic_auto),
                ),
                selected = theme,
                onSelect = onTheme,
            )

            // DEC-003, REQ-APP-012: what leaves the phone and what never does.
            SectionTitle(stringResource(R.string.settings_section_privacy))
            AcCard {
                PrivacyRow(R.drawable.ic_send, Tone.OK, stringResource(R.string.privacy_sent_title), stringResource(R.string.privacy_sent_body))
                PrivacyRow(R.drawable.ic_eyeoff, Tone.BAD, stringResource(R.string.privacy_never_title), stringResource(R.string.privacy_never_body))
            }

            SectionTitle(stringResource(R.string.settings_section_about))
            ListGroup {
                ListRow(stringResource(R.string.about_version), divider = false) {
                    Text(PersianFormat.version(versionName), color = c.muted)
                }
                ListRow(stringResource(R.string.about_source), divider = true) {
                    Text("\u2066${stringResource(R.string.source_url)}\u2069", style = AcTheme.type.mono, color = c.muted)
                }
                // DEC-008: open source under Apache-2.0 (LICENSE).
                ListRow(stringResource(R.string.about_license), divider = true) {
                    Text("\u2066${stringResource(R.string.license_name)}\u2069", color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun PrivacyRow(@DrawableRes icon: Int, tone: Tone, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LeadIcon(icon, tone)
        Column(Modifier.weight(1f)) {
            Text(title, style = AcTheme.type.title)
            Text(body, style = AcTheme.type.small, color = AcTheme.colors.muted)
        }
    }
}
