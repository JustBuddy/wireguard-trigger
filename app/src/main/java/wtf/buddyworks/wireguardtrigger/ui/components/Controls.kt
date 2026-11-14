package wtf.buddyworks.wireguardtrigger.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import wtf.buddyworks.wireguardtrigger.ui.theme.OneUi

/** One UI segmented control: a grey pill with a sliding white thumb behind the selection. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OneUi.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(colors.pill)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 500f), label = "thumb")
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                    .clip(CircleShape)
                    .background(if (colors.isDark) colors.selection else colors.card),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = OneUi.type.nav.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A labelled setting row above its control, as in One UI's settings cards. */
@Composable
fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = OneUi.type.itemTitle, color = OneUi.colors.textPrimary, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = OneUi.type.caption, color = OneUi.colors.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Title, optional summary and a One UI switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = OneUi.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OneUi.type.itemTitle, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = OneUi.type.itemSubtitle, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.onAccent,
                checkedBorderColor = colors.accent,
                uncheckedTrackColor = colors.pill,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedBorderColor = colors.textSecondary.copy(alpha = 0.6f),
            ),
        )
    }
}

/** Small rounded label, e.g. the phase name above the gauge. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (OneUi.colors.isDark) 0.22f else 0.13f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, style = OneUi.type.nav.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

/** Horizontal margin of cards inside a pane. */
val CardMargin = 12.dp
