package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.ColorBadge
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val topBarHeight = 48.dp
private val badgeOffsetX = 8.dp
private val badgeOffsetY = (-6).dp

@Composable
fun TopBar(crumbs: List<String>, unread: Int, onToggleNotifications: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(topBarHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) Label("/", color = colors.textTertiary)
                Label(crumb, color = if (index == crumbs.lastIndex) colors.textPrimary else colors.textTertiary, weight = FontWeight.Medium)
            }
        }
        Box {
            IconAction(AppIcon.Bell, onToggleNotifications, tint = colors.textPrimary)
            if (unread > 0) ColorBadge(unread, colors.danger, Modifier.align(Alignment.TopEnd).offset(badgeOffsetX, badgeOffsetY))
        }
    }
}
