package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.ToastItem
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.delay

private const val TOAST_MILLIS = 6_000L
private const val URGENT_TOAST_MILLIS = 15_000L
private val toastWidth = 360.dp
private val stripeWidth = 4.dp

fun notificationIcon(kind: NotificationKind): AppIcon = when (kind) {
    NotificationKind.FINISHED -> AppIcon.Check
    NotificationKind.NEEDS_ACTION -> AppIcon.Alert
    NotificationKind.ERROR -> AppIcon.Alert
    NotificationKind.INFO -> AppIcon.Info
}

@Composable
fun ToastHost(toasts: List<ToastItem>, onOpen: (ToastItem) -> Unit, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        toasts.forEach { toast -> Toast(toast, { onOpen(toast) }, { onDismiss(toast.id) }) }
    }
}

@Composable
private fun Toast(toast: ToastItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val tint = colors.notification(toast.kind)
    val urgent = toast.kind == NotificationKind.NEEDS_ACTION
    LaunchedEffect(toast.id) {
        delay(if (urgent) URGENT_TOAST_MILLIS else TOAST_MILLIS)
        onDismiss()
    }
    val shape = RoundedCornerShape(Radius.md)
    Pressable(onOpen, Modifier.width(toastWidth).border(1.dp, tint.copy(alpha = if (urgent) 1f else Alpha.BORDER_TINT), shape), shape, colors.surface, colors.surfaceHover) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(stripeWidth).fillMaxHeight().background(tint))
            Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Glyph(notificationIcon(toast.kind), tint, Modifier.padding(top = 2.dp), size = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(toast.title, weight = FontWeight.SemiBold)
                    if (toast.body.isNotBlank()) Label(toast.body, color = colors.textSecondary, size = FontSize.small, maxLines = 2)
                }
                IconAction(AppIcon.Close, onDismiss, Modifier.align(Alignment.Top), tint = colors.textTertiary)
            }
        }
    }
}
