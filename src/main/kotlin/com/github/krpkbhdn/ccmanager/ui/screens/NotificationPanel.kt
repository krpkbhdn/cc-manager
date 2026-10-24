package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.cardSurface
import com.github.krpkbhdn.ccmanager.ui.components.notificationIcon
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.relativeTime
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val panelWidth = 400.dp
private val panelMaxHeight = 520.dp
private val panelTopOffset = 52.dp
private val kindIconBox = 32.dp
private val unreadDot = 8.dp

@Composable
fun NotificationPanel(state: AppState, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.notificationsOpen = false },
    ) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = panelTopOffset, end = Spacing.lg).width(panelWidth).heightIn(max = panelMaxHeight)
                .cardSurface(RoundedCornerShape(Radius.lg)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            PanelHeader(state.unreadCount, state::markAllRead)
            HorizontalRule()
            if (state.notifications.isEmpty()) EmptyNotifications() else NotificationList(state)
        }
    }
}

@Composable
private fun PanelHeader(unread: Int, onMarkAllRead: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Label("Notifications", Modifier.weight(1f), size = FontSize.heading, weight = FontWeight.SemiBold)
        Pressable(onMarkAllRead, enabled = unread > 0) {
            Label("Mark all read", Modifier.padding(Spacing.sm), color = if (unread > 0) colors.accent else colors.textTertiary, size = FontSize.small)
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Label("You are all caught up", color = colors.textTertiary)
    }
}

@Composable
private fun NotificationList(state: AppState) {
    LazyColumn {
        items(state.notifications.asReversed(), key = { it.id }) { notification ->
            NotificationRow(notification, state.projectName(notification.projectId), relativeTime(notification.at, state.now), notification.id > state.readUpTo) {
                state.openNotification(notification)
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, projectName: String, age: String, unread: Boolean, onClick: () -> Unit) {
    val tint = colors.notification(notification.kind)
    val urgent = notification.kind == NotificationKind.NEEDS_ACTION
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), base = if (urgent && unread) tint.copy(alpha = Alpha.SOFT / 2) else Color.Transparent) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(kindIconBox).clip(CircleShape).background(tint.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(notificationIcon(notification.kind), tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Label(projectName, color = colors.textSecondary, size = FontSize.small, weight = FontWeight.Medium)
                    Label(age, color = colors.textTertiary, size = FontSize.caption)
                }
                Label(notification.title, weight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
                if (notification.body.isNotBlank()) Label(notification.body, color = colors.textSecondary, size = FontSize.small, maxLines = 2)
            }
            if (unread) Box(Modifier.padding(top = Spacing.sm).size(unreadDot).clip(CircleShape).background(colors.accent))
        }
    }
}
