package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

private val TRACK_WIDTH = 12.dp
private val THUMB_WIDTH = 5.dp
private val MIN_THUMB_HEIGHT = 28.dp
private const val SHOW_MILLIS = 1200L
private const val FADE_MILLIS = 250
private const val THUMB_ALPHA = 0.35f

/** Thin scrollbar for the scrollback; it fades in while the view scrolls and out shortly after, and its thumb can be dragged. */
@Composable
internal fun TerminalScrollbar(session: TerminalSession, modifier: Modifier = Modifier) {
    var recentlyScrolled by remember { mutableStateOf(false) }
    val position = session.scrollShown
    LaunchedEffect(position, session.scrollbackLines) {
        recentlyScrolled = true
        delay(SHOW_MILLIS)
        recentlyScrolled = false
    }
    val alpha by animateFloatAsState(if (recentlyScrolled && session.scrollbackLines > 0) 1f else 0f, tween(FADE_MILLIS))
    if (alpha == 0f) return
    Box(modifier.fillMaxHeight().width(TRACK_WIDTH).pointerInput(session) { detectThumbDrag(session) }) {
        Canvas(Modifier.fillMaxHeight().width(TRACK_WIDTH)) {
            val total = (session.scrollbackLines + session.rows).toFloat()
            val thumb = max(MIN_THUMB_HEIGHT.toPx(), size.height * session.rows / total)
            val top = (size.height - thumb) * (1f - session.scrollShown / session.scrollbackLines.coerceAtLeast(1))
            val width = THUMB_WIDTH.toPx()
            drawRoundRect(
                color = session.theme.foreground.copy(alpha = THUMB_ALPHA * alpha),
                topLeft = Offset(size.width - width - 2f, top),
                size = Size(width, thumb),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectThumbDrag(session: TerminalSession) {
    detectDragGestures { change, _ ->
        val thumb = max(MIN_THUMB_HEIGHT.toPx(), size.height * session.rows / (session.scrollbackLines + session.rows).toFloat())
        val fraction = ((change.position.y - thumb / 2) / (size.height - thumb)).coerceIn(0f, 1f)
        session.scrollJumpTo((1f - fraction) * session.scrollbackLines)
        change.consume()
    }
}
