package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.state.QuickAction
import com.github.krpkbhdn.ccmanager.ui.state.quickActions
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Slim shortcut row above the terminal; each chip types its keys into the console. */
@Composable
fun QuickActionsBar(enabled: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        quickActions.forEach { action -> QuickActionChip(action, enabled) { onSend(action.keys) } }
    }
}

@Composable
private fun QuickActionChip(action: QuickAction, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    WithTooltip(action.hint) {
        Pressable(onClick, Modifier.border(1.dp, colors.border, shape), shape, colors.surface, colors.surfaceHover, enabled) {
            Label(
                action.label, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                color = if (enabled) colors.textPrimary else colors.textTertiary, size = FontSize.small, weight = FontWeight.Medium,
            )
        }
    }
}
