package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.components.notificationIcon
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.relativeTime
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val drawerWidth = 400.dp
private val kindBox = 34.dp
private val unreadDot = 8.dp

/** Right-hand drawer with the notification history; clicking the dimmed area or Close hides it. */
@Composable
fun NotificationPanel(state: AppState, modifier: Modifier = Modifier) {
    AnimatedVisibility(state.notificationsOpen, modifier.fillMaxSize(), enter = fadeIn(Motion.base()), exit = fadeOut(Motion.base())) {
        Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = colors.scrim.alpha / 2)).clickable(remember { MutableInteractionSource() }, null) { state.notificationsOpen = false }) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(Motion.slow()) { it },
                exit = slideOutHorizontally(Motion.base()) { it },
            ) { Drawer(state) }
        }
    }
}

@Composable
private fun Drawer(state: AppState) {
    Row(
        Modifier.fillMaxHeight().width(drawerWidth).shadow(24.dp, ambientColor = colors.shadow, spotColor = colors.shadow).background(colors.sidebar)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        VerticalRule()
        Column(Modifier.weight(1f)) {
            DrawerHeader(state.unreadCount, state::markAllRead) { state.notificationsOpen = false }
            HorizontalRule()
            if (state.notifications.isEmpty()) {
                EmptyState(Icons.CircleCheck, "You are all caught up", "Finished work and requests for your decision will be listed here.", tint = colors.success)
            } else {
                NotificationList(state)
            }
        }
    }
}

@Composable
private fun DrawerHeader(unread: Int, onMarkAllRead: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("Notifications", Modifier.weight(1f), style = Type.subtitle)
        GhostButton("Mark all read", onMarkAllRead, enabled = unread > 0)
        IconButton(Icons.Close, onClose, tooltip = "Close", hint = "Esc")
    }
}

@Composable
private fun NotificationList(state: AppState) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.xxs), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm)) {
            items(state.notifications.asReversed(), key = { it.id }) { notification ->
                NotificationRow(notification, state.projectName(notification.projectId), relativeTime(notification.at, state.now), notification.id > state.readUpTo, Modifier.animateItem()) {
                    state.openNotification(notification)
                }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, projectName: String, age: String, unread: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = colors.notification(notification.kind)
    val urgent = notification.kind == NotificationKind.NEEDS_ACTION
    Pressable(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (urgent && unread) tint.copy(alpha = Alpha.FAINT) else Color.Transparent) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(kindBox).clip(RoundedCornerShape(Radius.md)).background(tint.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(notificationIcon(notification.kind), tint, size = IconSize.large)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Label(projectName, Modifier.weight(1f, fill = false), color = colors.textSecondary, style = Type.smallMedium)
                    Label(age, color = colors.textTertiary, style = Type.numericCaption)
                }
                Label(notification.title, style = if (unread) Type.heading else Type.body)
                if (notification.body.isNotBlank()) Label(notification.body, color = colors.textSecondary, style = Type.small, maxLines = 3)
                if (notification.ccmSessionId != null && urgent) {
                    Row(Modifier.padding(top = Spacing.xs)) { AppButton("Open terminal", onClick, icon = Icons.Terminal, compact = true) }
                }
            }
            if (unread) Box(Modifier.padding(top = Spacing.sm).size(unreadDot).background(colors.accent, CircleShape))
        }
    }
}
