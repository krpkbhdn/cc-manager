package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.state.statusLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val PULSE_MILLIS = 1100
private val dotSize = 8.dp
private val haloSize = 16.dp

/** Traffic-light dot; WORKING pulses gently, NEEDS_ACTION carries a halo. */
@Composable
fun StatusDot(status: AgentStatus, modifier: Modifier = Modifier, size: Dp = dotSize) {
    val tint = colors.status(status)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (status == AgentStatus.WORKING) Alpha.PULSE_LOW else 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(modifier.size(haloSize.coerceAtLeast(size)), contentAlignment = Alignment.Center) {
        if (status == AgentStatus.NEEDS_ACTION) Box(Modifier.size(haloSize).clip(CircleShape).background(tint.copy(alpha = Alpha.HALO)))
        Box(Modifier.size(size).alpha(pulse).clip(CircleShape).background(tint))
    }
}

@Composable
fun StatusChip(status: AgentStatus, modifier: Modifier = Modifier) {
    val tint = colors.status(status)
    Row(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(tint.copy(alpha = Alpha.SOFT))
            .padding(start = Spacing.xs, end = Spacing.md, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(Spacing.xs))
        Label(statusLabel(status), color = tint, size = FontSize.small, weight = FontWeight.Medium)
    }
}
