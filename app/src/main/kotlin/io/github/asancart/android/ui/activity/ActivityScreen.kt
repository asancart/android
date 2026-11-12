package io.github.asancart.android.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asancart.android.R
import io.github.asancart.android.core.capture.UnparsedSms
import io.github.asancart.android.ui.capture.label
import io.github.asancart.android.ui.components.AcIcon
import io.github.asancart.android.ui.components.BigTopBar
import io.github.asancart.android.ui.components.Choice
import io.github.asancart.android.ui.components.LeadIcon
import io.github.asancart.android.ui.components.ListGroup
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.UnderlineTabs
import io.github.asancart.android.ui.format.PersianFormat
import io.github.asancart.android.ui.format.TimeFormat
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.Tone

enum class ActivityTab { SENT, QUEUE, UNPARSED }

/**
 * Activity (design screen 12): sent Transfer Reports, the queue (REQ-APP-014) and unparsed SMS
 * (REQ-APP-015). The sent and queue lists fill in with sending (T4.4).
 */
@Composable
fun ActivityScreen(
    tab: ActivityTab,
    onTab: (ActivityTab) -> Unit,
    unparsed: List<UnparsedSms>,
    onOpenUnparsed: (String) -> Unit,
    onRereadAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BigTopBar(stringResource(R.string.activity_title))
        UnderlineTabs(
            choices = listOf(
                Choice(ActivityTab.SENT, stringResource(R.string.activity_tab_sent)),
                Choice(ActivityTab.QUEUE, stringResource(R.string.activity_tab_queue)),
                Choice(ActivityTab.UNPARSED, unparsedTabLabel(unparsed.size)),
            ),
            selected = tab,
            onSelect = onTab,
        )
        ScreenBody(Modifier.padding(top = 10.dp)) {
            when (tab) {
                ActivityTab.SENT -> Empty(stringResource(R.string.activity_sent_empty))
                ActivityTab.QUEUE -> Empty(stringResource(R.string.activity_queue_empty))
                ActivityTab.UNPARSED -> {
                    Text(stringResource(R.string.activity_unparsed_intro), style = AcTheme.type.small, color = AcTheme.colors.muted)
                    if (unparsed.isEmpty()) {
                        Empty(stringResource(R.string.activity_unparsed_empty))
                    } else {
                        ListGroup {
                            unparsed.forEachIndexed { i, u -> UnparsedRow(u, divider = i > 0, onClick = { onOpenUnparsed(u.id) }) }
                        }
                        TextButton(onClick = onRereadAll, modifier = Modifier.fillMaxWidth()) {
                            AcIcon(R.drawable.ic_refresh, tint = AcTheme.colors.primaryText)
                            Text(
                                stringResource(R.string.activity_reread_all),
                                style = AcTheme.type.button,
                                color = AcTheme.colors.primaryText,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun unparsedTabLabel(count: Int): String {
    val label = stringResource(R.string.activity_tab_unparsed)
    return if (count > 0) "$label ${PersianFormat.number(count.toLong())}" else label
}

@Composable
private fun UnparsedRow(u: UnparsedSms, divider: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
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
        LeadIcon(R.drawable.ic_sms, Tone.WARN)
        Column(Modifier.weight(1f)) {
            // The SMS text stays on this phone (DEC-003); showing it here is local.
            Text(previewLine(u.body), style = AcTheme.type.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "⁦${u.sender}⁩ · ${TimeFormat.smsTime(context, u.receivedAt)} · ${stringResource(u.reason.label())}",
                style = AcTheme.type.small,
                color = AcTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AcIcon(R.drawable.ic_chev, tint = AcTheme.colors.muted)
    }
}

/** The first line after a bank-name header, as in the design. */
private fun previewLine(body: String): String {
    val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return lines.getOrNull(1) ?: lines.firstOrNull().orEmpty()
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = AcTheme.type.small,
        color = AcTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
    )
}
