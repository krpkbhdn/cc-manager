package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.LocalAppColors
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import com.github.krpkbhdn.ccmanager.ui.web.input.ShortcutGuard
import com.github.krpkbhdn.ccmanager.ui.web.input.WebInputMapper
import com.github.krpkbhdn.ccmanager.ui.web.input.WebInputSink
import com.github.krpkbhdn.ccmanager.ui.web.input.keyModifierMask
import androidx.compose.runtime.collectAsState
import java.awt.Cursor
import java.awt.event.MouseEvent
import kotlin.math.roundToInt

private const val RESIZE_DEBOUNCE_MILLIS = 50L
private const val PAN_NOTCH_DP = 60.0
private val log = org.slf4j.LoggerFactory.getLogger("WebCanvas")
private val keyLookupWarned = java.util.concurrent.atomic.AtomicBoolean()

/** Draws the host's frame in physical pixels and forwards pointer, wheel and key input to it; a page larger than the canvas pans with the middle button, Space+drag or Shift+wheel and zooms with Ctrl+wheel. */
@Composable
fun WebCanvas(
    host: WebPageHost,
    viewport: PageViewport,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    device: DeviceSelection = DeviceSelection.Responsive,
    onPan: (dxDp: Double, dyDp: Double) -> Unit = { _, _ -> },
    onZoom: (cursorDp: Offset, notches: Int) -> Unit = { _, _ -> },
    onContextMenu: (pagePoint: Offset, canvasOffset: Offset) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current.density
    val frame by host.frame.collectAsState()
    val popup by host.popup.collectAsState()
    val cursor by host.cursor.collectAsState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val colors = LocalAppColors.current
    val focus = remember { FocusRequester() }
    val renderScale = viewport.renderScale(density)

    DisposableEffect(host) {
        host.setHidden(false)
        onDispose { host.setHidden(true) }
    }

    LaunchedEffect(host, size, viewport.widthCss, viewport.heightCss, renderScale) {
        if (size == IntSize.Zero) return@LaunchedEffect
        kotlinx.coroutines.delay(RESIZE_DEBOUNCE_MILLIS)
        host.resize(viewport.widthCss, viewport.heightCss, renderScale.toFloat())
    }

    // Frame pixels to displayed pixels; 1 only at 100% zoom with the pixel cap not reached.
    val display = density * viewport.scale / renderScale
    val originX = viewport.offsetX * density
    val originY = viewport.offsetY * density
    val quality = if (display == 1.0) FilterQuality.None else FilterQuality.Low
    Box(modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { size = it }.semantics { contentDescription = "Web page" }
                .then(if (interactive) Modifier.inputHandling(host, density, viewport, focus, cursor, onContextMenu, onPan, onZoom) else Modifier),
        ) {
            drawRect(if (device.responsive) colors.surface else colors.background)
            if (!device.responsive) {
                drawRect(
                    colors.surface, Offset(originX.toFloat(), originY.toFloat()),
                    Size((viewport.widthCss * viewport.scale * density).toFloat(), (viewport.heightCss * viewport.scale * density).toFloat()),
                )
            }
            // A frame of the previous size can outlive a rotate or resize; it must never paint outside the page.
            val pageW = (viewport.widthCss * viewport.scale * density).toFloat()
            val pageH = (viewport.heightCss * viewport.scale * density).toFloat()
            clipRect(originX.toFloat(), originY.toFloat(), originX.toFloat() + pageW, originY.toFloat() + pageH) {
                frame?.let {
                    drawImage(
                        it.bitmap,
                        dstOffset = IntOffset(originX.toInt(), originY.toInt()),
                        dstSize = IntSize((it.widthPx * display).roundToInt(), (it.heightPx * display).roundToInt()),
                        filterQuality = quality,
                    )
                }
                popup?.let {
                    drawImage(
                        it.frame.bitmap,
                        dstOffset = IntOffset((originX + it.xDip * viewport.scale * density).toInt(), (originY + it.yDip * viewport.scale * density).toInt()),
                        dstSize = IntSize((it.frame.widthPx * display).roundToInt(), (it.frame.heightPx * display).roundToInt()),
                        filterQuality = quality,
                    )
                }
            }
        }
        if (!device.responsive) {
            Label(
                sizeLabel(viewport), Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                color = colors.textTertiary, style = Type.caption,
            )
        }
    }
}

internal fun sizeLabel(viewport: PageViewport): String = "${viewport.widthCss} × ${viewport.heightCss} · ${(viewport.scale * 100).roundToInt()}%"

@Composable
private fun Modifier.inputHandling(
    host: WebPageHost,
    density: Float,
    viewport: PageViewport,
    focus: FocusRequester,
    cursor: Int,
    onContextMenu: (Offset, Offset) -> Unit,
    onPan: (Double, Double) -> Unit,
    onZoom: (Offset, Int) -> Unit,
): Modifier {
    val mapper = remember(host) { WebInputMapper(host.asSink()) }
    val guard = remember { ShortcutGuard() }
    val currentViewport = rememberUpdatedState(viewport)
    val currentContextMenu = rememberUpdatedState(onContextMenu)
    val currentPan = rememberUpdatedState(onPan)
    val currentZoom = rememberUpdatedState(onZoom)
    val spaceDown = remember { java.util.concurrent.atomic.AtomicBoolean() }
    return focusRequester(focus)
        .onFocusChanged {
            host.focus(it.isFocused)
            if (!it.isFocused) spaceDown.set(false)
        }
        .onPreviewKeyEvent { event ->
            if (event.key == Key.Spacebar) spaceDown.set(event.type == KeyEventType.KeyDown)
            forwardKey(host, guard, event)
        }
        .focusable()
        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursor)))
        // Keyed on stable values only: a restart mid-gesture would drop the pan and the held button.
        .pointerInput(mapper, density) {
            forwardPointer(mapper, density, { currentViewport.value }, focus, { page, canvas -> currentContextMenu.value(page, canvas) }, { dx, dy -> currentPan.value(dx, dy) }, { c, n -> currentZoom.value(c, n) }, spaceDown::get)
        }
}

private fun WebPageHost.asSink() = object : WebInputSink {
    override fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int) =
        this@asSink.mouse(awtId, xDip, yDip, button, clickCount, modifiers)

    override fun scrollBy(xDip: Int, yDip: Int, pixels: Int) = this@asSink.scrollBy(xDip, yDip, pixels)
}

private fun forwardKey(host: WebPageHost, guard: ShortcutGuard, event: KeyEvent): Boolean {
    val awt = awtEventOf(event)
    if (awt == null) {
        if (keyLookupWarned.compareAndSet(false, true)) log.warn("Cannot read the AWT key event behind a Compose key event; typing into the page will not work")
        return false
    }
    if (!guard.allows(awt)) return false
    host.key(awt)
    return true
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun PointerInputScope.forwardPointer(
    mapper: WebInputMapper,
    density: Float,
    viewport: () -> PageViewport,
    focus: FocusRequester,
    onContextMenu: (Offset, Offset) -> Unit,
    onPan: (Double, Double) -> Unit,
    onZoom: (Offset, Int) -> Unit,
    spaceDown: () -> Boolean,
) {
    awaitPointerEventScope {
        var secondarySwallowed = false
        var panning = false
        var pageHeld = 0
        var lastPan = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.first()
            val view = viewport()
            val canvasDp = Offset(change.position.x / density, change.position.y / density)
            val page = view.toPage(canvasDp)
            val x = page.x.toInt()
            val y = page.y.toInt()
            val inside = view.containsCanvas(canvasDp)
            val canPan = view.maxPanX > 0 || view.maxPanY > 0
            val shift = event.keyboardModifiers.isShiftPressed
            val keys = keyModifierMask(event.keyboardModifiers.isCtrlPressed, shift, event.keyboardModifiers.isAltPressed, event.keyboardModifiers.isMetaPressed)
            val secondary = event.button == PointerButton.Secondary
            val middle = event.button == PointerButton.Tertiary
            val spacePan = event.button == PointerButton.Primary && spaceDown()
            if (event.type == PointerEventType.Press) secondarySwallowed = false
            if (event.type == PointerEventType.Move && !event.changes.any { it.pressed }) pageHeld = 0
            when (event.type) {
                PointerEventType.Press -> if ((middle || spacePan) && inside && canPan) {
                    panning = true
                    lastPan = canvasDp
                } else if (inside) {
                    focus.requestFocus()
                    if (secondary && !shift) {
                        secondarySwallowed = true
                        onContextMenu(page, change.position)
                    } else {
                        pageHeld++
                        mapper.press(x, y, awtButton(event), keys, change.uptimeMillis)
                    }
                }
                PointerEventType.Release ->
                    if (panning && (middle || event.button == PointerButton.Primary)) panning = false
                    else if (secondary && secondarySwallowed) secondarySwallowed = false
                    else if (pageHeld > 0) {
                        pageHeld--
                        mapper.release(x, y, awtButton(event), keys)
                    }
                PointerEventType.Move ->
                    if (panning) {
                        onPan((lastPan.x - canvasDp.x).toDouble(), (lastPan.y - canvasDp.y).toDouble())
                        lastPan = canvasDp
                    } else if (inside || pageHeld > 0) mapper.move(x, y, keys)
                PointerEventType.Exit -> mapper.exit(x, y)
                PointerEventType.Scroll ->
                    if (event.keyboardModifiers.isCtrlPressed) onZoom(canvasDp, if (change.scrollDelta.y < 0) 1 else -1)
                    else if (shift && inside && view.maxPanX > 0) onPan(change.scrollDelta.y * PAN_NOTCH_DP, change.scrollDelta.x * PAN_NOTCH_DP)
                    else if (inside) mapper.wheel(x, y, change.scrollDelta.y)
            }
            if (event.type == PointerEventType.Release && !event.changes.any { it.pressed }) pageHeld = 0
            change.consume()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun awtButton(event: PointerEvent): Int = when (event.button) {
    PointerButton.Secondary -> MouseEvent.BUTTON3
    PointerButton.Tertiary -> MouseEvent.BUTTON2
    else -> MouseEvent.BUTTON1
}

/**
 * In Compose 1.12 `nativeKeyEvent` is an internal wrapper whose `nativeEvent` is the real AWT event, read reflectively;
 * rebuilding the event breaks key identification on Windows.
 */
private fun awtEventOf(event: KeyEvent): java.awt.event.KeyEvent? = runCatching {
    val wrapper = event.nativeKeyEvent
    wrapper.javaClass.getMethod("getNativeEvent").invoke(wrapper) as? java.awt.event.KeyEvent
}.getOrNull()
