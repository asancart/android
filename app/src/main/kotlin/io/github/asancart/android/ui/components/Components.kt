package io.github.asancart.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.asancart.android.ui.theme.AcShapes
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.Tone
import io.github.asancart.android.ui.theme.mix
import io.github.asancart.android.ui.theme.toneColor
import io.github.asancart.android.ui.theme.toneFill

/** Design icon (res/drawable/ic_*.xml), tinted. Decorative unless [description] is given. */
@Composable
fun AcIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = AcTheme.colors.ink, size: Dp = 20.dp, description: String? = null) {
    Icon(painter = painterResource(icon), contentDescription = description, tint = tint, modifier = modifier.size(size))
}

/** `.card`: surface with a hairline border. [flat] is the borderless surface-2 variant. */
@Composable
fun AcCard(modifier: Modifier = Modifier, flat: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = AcTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AcShapes.lg)
            .background(if (flat) c.surface2 else c.surface)
            .then(if (flat) Modifier else Modifier.border(1.dp, c.line, AcShapes.lg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** `.lead`: a 40 dp tile with an icon or mark, tinted by [tone]. */
@Composable
fun Lead(tone: Tone = Tone.NEUTRAL, content: @Composable () -> Unit) {
    val c = AcTheme.colors
    Box(
        modifier = Modifier.size(40.dp).clip(AcShapes.tile).background(c.toneFill(tone)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
fun LeadIcon(@DrawableRes icon: Int, tone: Tone = Tone.NEUTRAL) {
    val c = AcTheme.colors
    Lead(tone) { AcIcon(icon, tint = if (tone == Tone.NEUTRAL) c.ink else c.toneColor(tone)) }
}

/** `.pill`: short status label with an optional dot. */
@Composable
fun AcPill(text: String, tone: Tone = Tone.NEUTRAL, dot: Boolean = true) {
    val c = AcTheme.colors
    val fg = c.toneColor(tone)
    Row(
        modifier = Modifier.heightIn(min = 24.dp).clip(AcShapes.pill).background(c.toneFill(tone)).padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(text, style = AcTheme.type.caption, color = fg, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** `.banner`: a tinted message box; [tone] WARN is the default look in the design. */
@Composable
fun AcBanner(@DrawableRes icon: Int, title: String, modifier: Modifier = Modifier, tone: Tone = Tone.WARN, body: String? = null) {
    val c = AcTheme.colors
    val fg = c.toneColor(tone)
    val (fillPart, borderAlpha) = when (tone) {
        Tone.OK -> 0.10f to 0.28f
        Tone.BAD -> 0.09f to 0.28f
        Tone.INFO -> 0.10f to 0.25f
        else -> 0.12f to 0.30f
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AcShapes.md)
            .background(mix(c.surface, fg, fillPart))
            .border(1.dp, fg.copy(alpha = borderAlpha), AcShapes.md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AcIcon(icon, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AcTheme.type.title)
            if (body != null) Text(body, style = AcTheme.type.small)
        }
    }
}

/** `.t-sec`: muted label above a group of cards. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = AcTheme.type.section,
        color = AcTheme.colors.muted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
    )
}

/** `.stat`: equal tiles with a value over a label. */
@Composable
fun StatRow(vararg stats: Pair<String, String>) {
    val c = AcTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for ((value, label) in stats) {
            Column(
                modifier = Modifier.weight(1f).clip(AcShapes.tile).background(c.surface2).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(value, style = AcTheme.type.stat, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = AcTheme.type.statLabel, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Profile on/off switch (REQ-APP-027), 52 x 32 as in the design. */
@Composable
fun AcSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, description: String) {
    val c = AcTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            checkedBorderColor = c.primary,
            uncheckedThumbColor = c.muted,
            uncheckedTrackColor = c.surface2,
            uncheckedBorderColor = c.muted,
            disabledUncheckedThumbColor = c.muted.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = c.surface2.copy(alpha = 0.4f),
            disabledUncheckedBorderColor = c.muted.copy(alpha = 0.4f),
            disabledCheckedThumbColor = c.onPrimary.copy(alpha = 0.4f),
            disabledCheckedTrackColor = c.primary.copy(alpha = 0.4f),
            disabledCheckedBorderColor = c.primary.copy(alpha = 0.4f),
        ),
    )
}

/** `.btn.block`: the main action of a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AcTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 46.dp),
        shape = AcShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        Text(text, style = AcTheme.type.button)
    }
}

/** `.abar.big`: large title of a tab screen, with optional actions at the end. */
@Composable
fun BigTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AcTheme.type.screenTitle, modifier = Modifier.weight(1f))
        actions()
    }
}

/** `.list`: rows in one bordered surface, separated by hairlines. */
@Composable
fun ListGroup(content: @Composable ColumnScope.() -> Unit) {
    val c = AcTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(AcShapes.lg).background(c.surface).border(1.dp, c.line, AcShapes.lg),
        content = content,
    )
}

/** `.li`: one row in a [ListGroup]; [divider] draws the hairline above it. */
@Composable
fun ListRow(title: String, divider: Boolean, trailing: @Composable () -> Unit = {}) {
    if (divider) HorizontalDivider(thickness = 1.dp, color = AcTheme.colors.line)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = AcTheme.type.bodyMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}
