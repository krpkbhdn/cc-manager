package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ToastItem
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val TOAST_MILLIS = 6_000
private const val URGENT_TOAST_MILLIS = 15_000
private val toastWidth = 372.dp
private val kindBox = 32.dp
private val progressHeight = 2.dp
private val toastElevation = 18.dp

fun notificationIcon(kind: NotificationKind): ImageVector = when (kind) {
    NotificationKind.FINISHED -> Icons.CircleCheck
    NotificationKind.NEEDS_ACTION -> Icons.Hand
    NotificationKind.ERROR -> Icons.Alert
    NotificationKind.INFO -> Icons.Info
}

/** Bottom-right stack of toasts; each one slides in, counts down on a hairline and slides out. */
@Composable
fun ToastHost(toasts: List<ToastItem>, onOpen: (ToastItem) -> Unit, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.End) {
        toasts.forEach { toast -> key(toast.id) { Toast(toast, { onOpen(toast) }, { onDismiss(toast.id) }) } }
    }
}

@Composable
private fun Toast(toast: ToastItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState) onDismiss()
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val urgent = toast.kind == NotificationKind.NEEDS_ACTION
    val total = if (urgent) URGENT_TOAST_MILLIS else TOAST_MILLIS
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(hovered) {
        if (hovered) return@LaunchedEffect
        remaining.animateTo(0f, tween((total * remaining.value).toInt(), easing = LinearEasing))
        visible.targetState = false
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = slideInHorizontally(Motion.slow()) { it / 2 } + fadeIn(Motion.base()),
        exit = slideOutHorizontally(Motion.base()) { it / 2 } + fadeOut(Motion.fast()) + shrinkVertically(Motion.base()),
    ) {
        ToastCard(toast, urgent, remaining.value, onOpen, { visible.targetState = false }, Modifier.hoverable(source))
    }
}

@Composable
private fun ToastCard(toast: ToastItem, urgent: Boolean, remaining: Float, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val tint = colors.notification(toast.kind)
    val shape = RoundedCornerShape(Radius.lg)
    val edge = if (urgent) tint.copy(alpha = Alpha.GLOW) else colors.borderStrong
    Pressable(
        onOpen,
        modifier.width(toastWidth).shadow(toastElevation, shape, ambientColor = colors.shadow, spotColor = if (urgent) tint.copy(alpha = Alpha.BORDER_TINT) else colors.shadow)
            .border(1.dp, edge, shape),
        shape, colors.surfaceRaised, colors.surfaceHover,
    ) {
        Column {
            Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.size(kindBox).clip(RoundedCornerShape(Radius.md)).background(tint.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                    Glyph(notificationIcon(toast.kind), tint, size = IconSize.large)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Label(toast.title, style = Type.heading, tooltipWhenTruncated = true)
                    if (toast.body.isNotBlank()) Label(toast.body, color = colors.textSecondary, style = Type.small, maxLines = 3)
                    if (toast.ccmSessionId != null) {
                        Row(Modifier.padding(top = Spacing.xs)) { AppButton("Open terminal", onOpen, variant = ButtonVariant.SECONDARY, icon = Icons.Terminal, compact = true) }
                    }
                }
                IconButton(Icons.Close, onClose, Modifier.align(Alignment.Top), tint = colors.textTertiary, size = 24.dp)
            }
            Box(Modifier.fillMaxWidth().height(progressHeight)) {
                Box(Modifier.fillMaxWidth(remaining).height(progressHeight).background(tint.copy(alpha = Alpha.GLOW)))
            }
        }
    }
}
