package io.github.asancart.android.ui.templates

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
import io.github.asancart.android.R
import io.github.asancart.android.ui.components.AcCard
import io.github.asancart.android.ui.components.BigTopBar
import io.github.asancart.android.ui.components.LeadIcon
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.SectionTitle
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.Tone

/**
 * Templates (design screen 14). Official Parser Templates arrive from the Profile's target
 * (REQ-APP-003, REQ-DEV-004) and custom ones are made on the phone (DEC-014); both come with the
 * template engine in T4.2. Until then this shows that none are on the phone yet.
 */
@Composable
fun TemplatesScreen() {
    Column(Modifier.fillMaxSize()) {
        BigTopBar(stringResource(R.string.templates_title))
        ScreenBody {
            AcCard(flat = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LeadIcon(R.drawable.ic_info, Tone.INFO)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.templates_official_none_title), style = AcTheme.type.title)
                        Text(stringResource(R.string.templates_official_none_body), style = AcTheme.type.caption, color = AcTheme.colors.muted)
                    }
                }
            }
            SectionTitle(stringResource(R.string.templates_section_custom))
            Text(stringResource(R.string.templates_custom_none), style = AcTheme.type.small, color = AcTheme.colors.muted)
        }
    }
}
