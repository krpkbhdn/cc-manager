package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.state.avatarGradient
import com.github.krpkbhdn.ccmanager.ui.state.avatarInitials
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val CORNER_RATIO = 0.3f
private const val LETTER_RATIO = 0.4f
private const val DOT_RATIO = 0.3f
private const val DOT_OFFSET_RATIO = 0.08f
private const val RING_RATIO = 0.07f
private const val INNER_LIGHT = 0.28f

/** Rounded-square letter avatar tinted from the project name, or from [gradient] when given; [status] adds a traffic-light dot on the corner. */
@Composable
fun ProjectAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 32.dp, status: AgentStatus? = null, cutout: Color = colors.sidebar, gradient: Pair<Long, Long>? = null) {
    val (top, bottom) = gradient ?: remember(name) { avatarGradient(name) }
    val initials = remember(name) { avatarInitials(name) }
    val shape = RoundedCornerShape(size * CORNER_RATIO)
    Box(modifier.size(size)) {
        Box(
            Modifier.size(size).clip(shape).background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                .border(1.dp, Color.White.copy(alpha = INNER_LIGHT), shape),
            contentAlignment = Alignment.Center,
        ) {
            Label(initials, color = Color.White, style = Type.heading.copy(fontSize = (size.value * LETTER_RATIO).sp, fontWeight = FontWeight.SemiBold, lineHeight = (size.value * LETTER_RATIO * 1.3f).sp))
        }
        if (status != null && status != AgentStatus.OFFLINE) {
            val dot = size * DOT_RATIO
            val ring = size * RING_RATIO
            Box(
                Modifier.align(Alignment.BottomEnd).offset(size * DOT_OFFSET_RATIO, size * DOT_OFFSET_RATIO)
                    .size(dot + ring * 2).background(cutout, CircleShape),
                contentAlignment = Alignment.Center,
            ) { StatusDot(status, size = dot) }
        }
    }
}
