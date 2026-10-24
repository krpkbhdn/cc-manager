package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val HORIZONTAL_PADDING = 12.dp
private val VERTICAL_PADDING = 8.dp
private const val RESIZE_DEBOUNCE_MILLIS = 60L
private const val SCROLL_ANIMATION_MILLIS = 110

/**
 * Embedded terminal of an opened session (see TerminalHost.open), drawn in plain Compose. The session keeps the
 * buffer, scrollback and selection, so leaving composition and coming back shows the same terminal.
 * [background] and [foreground] override the default colors of the scheme so the terminal can match the app surface.
 */
@Composable
fun TerminalView(
    ccmSessionId: String,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    background: Color? = null,
    foreground: Color? = null,
) {
    val session = remember(ccmSessionId) { TerminalHost.sessionOf(ccmSessionId) } ?: return
    TerminalSessionView(session, modifier, TerminalTheme(dark, background, foreground))
}

/** The terminal of [session] drawn with [theme]; separate from [TerminalView] so it can be shown without a launched process. */
@Composable
internal fun TerminalSessionView(session: TerminalSession, modifier: Modifier, theme: TerminalTheme) {
    SideEffect { session.theme = theme }
    val density = LocalDensity.current.density
    val fonts = remember(session.cellDp, density) { TerminalFonts((session.cellDp * density).roundToInt().coerceAtLeast(1)) }
    val geometry = remember(fonts, density) { geometryFor(fonts, density) }
    val painter = remember(fonts, geometry) { TerminalPainter(fonts, geometry.paddingX, geometry.paddingY) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val geometryState = rememberUpdatedState(geometry)
    val sizeState = rememberUpdatedState(viewSize)
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }

    FitToView(session, painter, geometry, viewSize)
    AnimateScroll(session)
    DisposableEffect(session) { onDispose { session.focused = false } }
    LaunchedEffect(session) { runCatching { session.focusRequester.requestFocus() } }

    Box(
        modifier.background(session.theme.background).onSizeChanged { viewSize = it }
            .focusRequester(session.focusRequester).onFocusChanged { session.focused = it.isFocused }.focusable()
            .onPreviewKeyEvent { event -> event.toKeyInput()?.let(session::onKey) ?: false }
            .terminalPointer(session, geometryState, sizeState) { menuAt = IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
            .pointerHoverIcon(PointerIcon.Text),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            session.frame
            drawIntoCanvas { painter.paint(it.skiaCanvas, size.width.roundToInt(), size.height.roundToInt(), session, session.focused) }
        }
        TerminalScrollbar(session, Modifier.align(Alignment.CenterEnd))
        menuAt?.let { TerminalContextMenu(session, it) { menuAt = null } }
    }
}

private fun geometryFor(fonts: TerminalFonts, density: Float) =
    TerminalGeometry(fonts.cellWidth, fonts.cellHeight, (HORIZONTAL_PADDING.value * density).roundToInt(), (VERTICAL_PADDING.value * density).roundToInt())

@Composable
private fun FitToView(session: TerminalSession, painter: TerminalPainter, geometry: TerminalGeometry, viewSize: IntSize) {
    var firstFit by remember(session) { mutableStateOf(true) }
    LaunchedEffect(session, painter, viewSize) {
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        if (!firstFit) delay(RESIZE_DEBOUNCE_MILLIS)
        firstFit = false
        val (columns, rows) = geometry.gridFor(viewSize.width, viewSize.height)
        session.fit(columns, rows)
    }
}

@Composable
private fun AnimateScroll(session: TerminalSession) {
    LaunchedEffect(session) {
        snapshotFlow { session.scrollTarget }.collectLatest { target ->
            animate(session.scrollShown, target, animationSpec = tween(SCROLL_ANIMATION_MILLIS, easing = FastOutSlowInEasing)) { value, _ ->
                session.showScrollAt(value)
            }
        }
    }
}

/** True while the keyboard focus sits inside any session terminal. */
fun terminalHasFocus(): Boolean = TerminalHost.anyFocused()
