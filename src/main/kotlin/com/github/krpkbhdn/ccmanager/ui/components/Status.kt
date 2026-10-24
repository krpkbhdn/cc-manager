package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.state.statusLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dotSize = 8.dp
private const val WORKING_RING_SCALE = 2.2f
private const val ATTENTION_RING_SCALE = 3.2f
private const val ATTENTION_MILLIS = 1100
private const val STATIC_HALO_SCALE = 1.9f
private const val RING_ALPHA = 0.55f
private const val OFFLINE_STROKE_DIVISOR = 4.5f

/**
 * Traffic-light dot. Idle is a calm solid dot, working breathes an expanding amber ring, needs-action throws a faster,
 * stronger red pulse over a standing halo, offline is a hollow ring. Halos are drawn outside the layout box.
 */
@Composable
fun StatusDot(status: AgentStatus, modifier: Modifier = Modifier, size: Dp = dotSize) {
    val tint = colors.status(status)
    val period = if (status == AgentStatus.NEEDS_ACTION) ATTENTION_MILLIS else Motion.PULSE
    val ringScale = if (status == AgentStatus.NEEDS_ACTION) ATTENTION_RING_SCALE else WORKING_RING_SCALE
    val pulse by rememberInfiniteTransition(label = "statusPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "statusPulseValue",
    )
    val animated = status == AgentStatus.WORKING || status == AgentStatus.NEEDS_ACTION
    Spacer(
        modifier.size(size).drawBehind {
            val radius = this.size.minDimension / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
            if (status == AgentStatus.OFFLINE) {
                drawCircle(tint, radius * 0.8f, center, style = Stroke(this.size.minDimension / OFFLINE_STROKE_DIVISOR))
                return@drawBehind
            }
            if (status != AgentStatus.WORKING) drawCircle(tint.copy(alpha = Alpha.SOFT), radius * STATIC_HALO_SCALE, center)
            if (animated) drawCircle(tint.copy(alpha = RING_ALPHA * (1f - pulse)), radius * (1f + (ringScale - 1f) * pulse), center)
            drawCircle(tint, radius, center)
        },
    )
}

/** Pill with a status dot and its label. */
@Composable
fun StatusChip(status: AgentStatus, modifier: Modifier = Modifier) {
    val tint = colors.status(status)
    Row(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(tint.copy(alpha = Alpha.SOFT)).padding(start = Spacing.sm, end = Spacing.sm + 2.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, size = 6.dp)
        Spacer(Modifier.width(Spacing.sm - 1.dp))
        Label(statusLabel(status), color = tint, style = Type.captionMedium)
    }
}
