package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.QuickAction
import com.github.krpkbhdn.ccmanager.ui.state.quickActions
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val chipHeight = 28.dp
private val COMPACT_BELOW = 800.dp

private fun iconOf(action: QuickAction): ImageVector = when (action.label) {
    "Interrupt" -> Icons.Stop
    "Compact" -> Icons.Compact
    "Clear" -> Icons.Eraser
    "Resume" -> Icons.Rewind
    "Plan mode" -> Icons.Tasks
    "Model" -> Icons.Cpu
    "Agents" -> Icons.Bot
    else -> Icons.Book
}

/** Collapsible shortcut strip above the terminal; each chip types its keys into the console. */
@Composable
fun QuickActionsBar(visible: Boolean, enabled: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = expandVertically(Motion.base()) + fadeIn(Motion.base()), exit = shrinkVertically(Motion.base()) + fadeOut(Motion.fast())) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < COMPACT_BELOW
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xs + 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quickActions.forEach { action -> QuickActionChip(action, enabled, compact) { onSend(action.keys) } }
            }
        }
    }
}

@Composable
private fun QuickActionChip(action: QuickAction, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    WithTooltip(if (compact) "${action.label}  ${action.hint}" else action.hint) {
        Pressable(onClick, Modifier.height(chipHeight).alpha(if (enabled) 1f else Alpha.DISABLED).border(1.dp, colors.border, shape), shape, colors.surface, colors.surfaceHover, enabled) {
            Row(
                Modifier.padding(horizontal = if (compact) Spacing.md - 2.dp else Spacing.md - 1.dp).height(chipHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
            ) {
                Glyph(iconOf(action), colors.textSecondary, size = IconSize.small)
                if (!compact) Label(action.label, color = colors.textPrimary, style = Type.smallMedium)
            }
        }
    }
}
