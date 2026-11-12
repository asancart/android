package org.asancart.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.asancart.android.LocalAppContainer
import org.asancart.android.R
import org.asancart.android.ui.components.AcBanner
import org.asancart.android.ui.components.AcCard
import org.asancart.android.ui.components.AcPill
import org.asancart.android.ui.components.AcSwitch
import org.asancart.android.ui.components.Lead
import org.asancart.android.ui.components.LeadIcon
import org.asancart.android.ui.components.ScreenBody
import org.asancart.android.ui.components.SectionTitle
import org.asancart.android.ui.components.StatRow
import org.asancart.android.ui.format.PersianFormat
import org.asancart.android.ui.theme.AcTheme
import org.asancart.android.ui.theme.AsancartMark
import org.asancart.android.ui.theme.Tone

@Composable
fun HomeRoute() {
    val container = LocalAppContainer.current
    val vm = viewModel { HomeViewModel(container.profiles, container.smsPermissions::granted) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, onToggle = vm::setEnabled)
}

/**
 * Home (design screen 10, REQ-APP-029). Not yet here: the new-Profile button and "continue setup"
 * (pairing, T4.3), the failing-Profile and unparsed-SMS banners (T4.4, T4.2).
 */
@Composable
fun HomeScreen(state: HomeUiState, onToggle: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader()
        ScreenBody {
            if (state.showAllOk) AcBanner(R.drawable.ic_check, stringResource(R.string.home_all_ok), tone = Tone.OK)
            SectionTitle(stringResource(R.string.home_section_profiles))
            for (profile in state.profiles) ProfileCardView(profile, onToggle)
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 34 dp is under the full mark's 48 dp minimum, so the small mark (BRAND-007).
        AsancartMark(small = true, modifier = Modifier.width(34.dp))
        Text(stringResource(R.string.app_name), style = AcTheme.type.brand)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCardView(profile: ProfileCard, onToggle: (String, Boolean) -> Unit) {
    val c = AcTheme.colors
    val none = "—"
    val notConnected = stringResource(R.string.profile_not_connected)
    AcCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profile.isPlatform) {
                Lead(Tone.PRIMARY) { AsancartMark(small = true, modifier = Modifier.width(28.dp)) }
            } else {
                LeadIcon(R.drawable.ic_code)
            }
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = AcTheme.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        append(profile.shopName ?: notConnected)
                        append(" · ")
                        // Host stays LTR inside the RTL line (U+2066..U+2069 isolate it).
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) { append("\u2066${profile.host}\u2069") }
                    },
                    style = AcTheme.type.caption,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AcSwitch(
                checked = profile.enabled,
                onCheckedChange = { onToggle(profile.id, it) },
                enabled = profile.enabled || profile.canTurnOn,
                description = stringResource(R.string.profile_switch_label, profile.name),
            )
        }
        StatRow(
            PersianFormat.number(profile.queueLength.toLong()) to stringResource(R.string.profile_stat_queue),
            none to stringResource(R.string.profile_stat_last_sms),
            none to stringResource(R.string.profile_stat_last_signal),
        )
        val showChips = profile.boundCards.isNotEmpty() || !profile.enabled
        if (showChips) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (card in profile.boundCards) AcPill(PersianFormat.digits(card), dot = false)
                if (!profile.enabled) AcPill(stringResource(R.string.profile_off))
            }
        }
        if (!profile.setupFinished) AcPill(stringResource(R.string.profile_setup_unfinished), Tone.WARN)
    }
}
