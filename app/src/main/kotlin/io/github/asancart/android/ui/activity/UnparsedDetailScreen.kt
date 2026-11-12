package io.github.asancart.android.ui.activity

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.asancart.android.R
import io.github.asancart.android.core.capture.UnparsedSms
import io.github.asancart.android.ui.capture.explanation
import io.github.asancart.android.ui.capture.label
import io.github.asancart.android.ui.components.AcBanner
import io.github.asancart.android.ui.components.AcCard
import io.github.asancart.android.ui.components.BackTopBar
import io.github.asancart.android.ui.components.LeadIcon
import io.github.asancart.android.ui.components.ListGroup
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.SectionTitle
import io.github.asancart.android.ui.format.TimeFormat
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.Tone

/**
 * Unparsed SMS (design screen 13, C10): the text, which stays on the phone, the reason in plain
 * words, the templates tried, and what to do. Re-read and dismiss are REQ-APP-015; making a custom
 * template from the SMS comes with the template editor.
 */
@Composable
fun UnparsedDetailScreen(u: UnparsedSms, onBack: () -> Unit, onReread: () -> Unit, onDismiss: () -> Unit) {
    val c = AcTheme.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.unparsed_title), onBack)
        ScreenBody {
            AcCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LeadIcon(R.drawable.ic_sms, Tone.WARN)
                    Column(Modifier.weight(1f)) {
                        Text("⁦${u.sender}⁩", style = AcTheme.type.title)
                        Text(TimeFormat.smsTime(context, u.receivedAt), style = AcTheme.type.caption, color = c.muted)
                    }
                }
                Text(
                    u.body,
                    style = AcTheme.type.small.copy(lineHeight = AcTheme.type.body.lineHeight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                        .background(c.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            AcBanner(R.drawable.ic_eyeoff, stringResource(R.string.unparsed_local_only), tone = Tone.INFO)

            SectionTitle(stringResource(R.string.unparsed_why))
            AcCard {
                Text(stringResource(u.reason.explanation()), style = AcTheme.type.small)
                KeyValue(stringResource(R.string.unparsed_templates_tried), u.templatesTried.joinToString("، ").ifEmpty { stringResource(R.string.unparsed_none_tried) })
                KeyValue(stringResource(R.string.unparsed_result), stringResource(u.reason.label()))
            }

            SectionTitle(stringResource(R.string.unparsed_what_now))
            ListGroup {
                Action(R.drawable.ic_refresh, Tone.NEUTRAL, stringResource(R.string.unparsed_reread), stringResource(R.string.unparsed_reread_sub), divider = false, onClick = onReread)
                Action(R.drawable.ic_trash, Tone.BAD, stringResource(R.string.unparsed_dismiss), null, divider = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(key, style = AcTheme.type.small, color = AcTheme.colors.muted)
        // Template names are Latin layout keys; keep them LTR inside the RTL line.
        Text("⁦$value⁩", style = AcTheme.type.small, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Action(@DrawableRes icon: Int, tone: Tone, title: String, sub: String?, divider: Boolean, onClick: () -> Unit) {
    if (divider) HorizontalDivider(thickness = 1.dp, color = AcTheme.colors.line)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadIcon(icon, tone)
        Column(Modifier.weight(1f)) {
            Text(title, style = AcTheme.type.bodyMedium)
            if (sub != null) Text(sub, style = AcTheme.type.small, color = AcTheme.colors.muted)
        }
    }
}
