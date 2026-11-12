package io.github.asancart.android.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.asancart.android.R
import io.github.asancart.android.ui.components.AcCard
import io.github.asancart.android.ui.components.AcIcon
import io.github.asancart.android.ui.components.BigTopBar
import io.github.asancart.android.ui.components.Choice
import io.github.asancart.android.ui.components.LeadIcon
import io.github.asancart.android.ui.components.ListGroup
import io.github.asancart.android.ui.components.ListRow
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.SectionTitle
import io.github.asancart.android.ui.components.SegmentedChoice
import io.github.asancart.android.ui.format.PersianFormat
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.ThemeChoice
import io.github.asancart.android.ui.theme.Tone

/**
 * Settings (design screen 16). Not yet here: the automatic template update switch and
 * Settings -> Logs (REQ-OPS-012, T4.6).
 */
@Composable
fun SettingsScreen(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit, versionName: String, missingPermissions: Int, onOpenPermissions: () -> Unit) {
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

            SectionTitle(stringResource(R.string.settings_section_app))
            ListGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onOpenPermissions)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LeadIcon(R.drawable.ic_shield, if (missingPermissions > 0) Tone.BAD else Tone.OK)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_permissions), style = AcTheme.type.bodyMedium)
                        Text(
                            if (missingPermissions > 0) {
                                stringResource(R.string.settings_permissions_missing, PersianFormat.number(missingPermissions.toLong()))
                            } else {
                                stringResource(R.string.settings_permissions_ok)
                            },
                            style = AcTheme.type.small,
                            color = c.muted,
                        )
                    }
                    AcIcon(R.drawable.ic_chev, tint = c.muted)
                }
            }

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
