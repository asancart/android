package io.github.asancart.android.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.asancart.android.R
import io.github.asancart.android.ui.components.BigTopBar
import io.github.asancart.android.ui.components.Choice
import io.github.asancart.android.ui.components.ScreenBody
import io.github.asancart.android.ui.components.UnderlineTabs
import io.github.asancart.android.ui.theme.AcTheme

enum class ActivityTab { SENT, QUEUE, UNPARSED }

/**
 * Activity (design screen 12): sent Transfer Reports, the queue (REQ-APP-014) and unparsed SMS
 * (REQ-APP-015). Lists fill in with capture (T4.2) and the queue (T4.4); empty until then.
 */
@Composable
fun ActivityScreen(initialTab: ActivityTab = ActivityTab.SENT) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        BigTopBar(stringResource(R.string.activity_title))
        UnderlineTabs(
            choices = listOf(
                Choice(ActivityTab.SENT, stringResource(R.string.activity_tab_sent)),
                Choice(ActivityTab.QUEUE, stringResource(R.string.activity_tab_queue)),
                Choice(ActivityTab.UNPARSED, stringResource(R.string.activity_tab_unparsed)),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
        ScreenBody(Modifier.padding(top = 10.dp)) {
            when (tab) {
                ActivityTab.SENT -> Empty(stringResource(R.string.activity_sent_empty))
                ActivityTab.QUEUE -> Empty(stringResource(R.string.activity_queue_empty))
                ActivityTab.UNPARSED -> {
                    Text(stringResource(R.string.activity_unparsed_intro), style = AcTheme.type.small, color = AcTheme.colors.muted)
                    Empty(stringResource(R.string.activity_unparsed_empty))
                }
            }
        }
    }
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
