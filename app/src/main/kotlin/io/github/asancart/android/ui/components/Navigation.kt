package io.github.asancart.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.asancart.android.ui.format.PersianFormat
import io.github.asancart.android.ui.theme.AcShapes
import io.github.asancart.android.ui.theme.AcTheme
import io.github.asancart.android.ui.theme.mix

/** [badge] > 0 shows a count on the icon (design: unparsed SMS on Activity). */
data class NavItem<T>(val key: T, val label: String, @param:DrawableRes val icon: Int, val badge: Int = 0)

/** `.nav`: bottom navigation of the four tabs (design C8). */
@Composable
fun <T> BottomNav(items: List<NavItem<T>>, selected: T, onSelect: (T) -> Unit) {
    val c = AcTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 4.dp)
                .selectableGroup(),
        ) {
            for (item in items) {
                val on = item.key == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(item.key) })
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 30.dp)
                            .clip(AcShapes.pill)
                            .background(if (on) c.primary.copy(alpha = 0.18f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        AcIcon(item.icon, tint = if (on) c.primaryText else c.muted, size = 24.dp)
                        if (item.badge > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 8.dp)
                                    .heightIn(min = 18.dp)
                                    .clip(AcShapes.pill)
                                    .background(c.bad)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(PersianFormat.number(item.badge.toLong()), style = AcTheme.type.caption, fontWeight = FontWeight.Bold, color = c.surface)
                            }
                        }
                    }
                    Text(
                        item.label,
                        style = AcTheme.type.nav,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) c.ink else c.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

data class Choice<T>(val key: T, val label: String, @param:DrawableRes val icon: Int? = null)

/** `.seg`: one choice out of a few, in a pill outline. */
@Composable
fun <T> SegmentedChoice(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    val c = AcTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(AcShapes.pill)
            .border(1.5.dp, c.line, AcShapes.pill)
            .selectableGroup(),
    ) {
        choices.forEachIndexed { index, choice ->
            val on = choice.key == selected
            if (index > 0) Spacer(Modifier.width(1.5.dp).fillMaxHeight().background(c.line))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .background(if (on) mix(c.surface, c.primary, 0.14f) else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(choice.key) }),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (choice.icon != null) AcIcon(choice.icon, tint = if (on) c.primaryText else c.ink)
                Text(
                    choice.label,
                    style = AcTheme.type.tab,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) c.primaryText else c.ink,
                )
            }
        }
    }
}

/** `.tabs`: underlined tabs inside a screen (Activity). */
@Composable
fun <T> UnderlineTabs(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    val c = AcTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup()) {
            for (choice in choices) {
                val on = choice.key == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(choice.key) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        choice.label,
                        style = AcTheme.type.tab,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) c.primaryText else c.muted,
                    )
                    if (on) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(c.primary),
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}
