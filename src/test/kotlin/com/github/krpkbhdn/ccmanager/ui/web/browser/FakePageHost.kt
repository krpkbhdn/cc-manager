package com.github.krpkbhdn.ccmanager.ui.web.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.awt.Cursor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** One recorded [FakePageHost.mouse] call. */
data class MouseCall(val awtId: Int, val xDip: Int, val yDip: Int, val button: Int, val clickCount: Int, val modifiers: Int)

/** One recorded [FakePageHost.resize] call. */
data class ResizeCall(val widthDip: Int, val heightDip: Int, val density: Float)

/** One recorded [FakePageHost.scrollBy] call. */
data class ScrollCall(val xDip: Int, val yDip: Int, val pixels: Int)

/** Deterministic [WebPageHost] for tests: state is set directly, every command is recorded in a list. */
class FakePageHost : WebPageHost {
    override val page = MutableStateFlow(PageState(url = "about:blank"))
    override val frame = MutableStateFlow<BrowserFrame?>(null)
    override val popup = MutableStateFlow<PopupFrame?>(null)
    override val cursor = MutableStateFlow(Cursor.DEFAULT_CURSOR)
    override val events = MutableSharedFlow<PageEvent>(extraBufferCapacity = EVENT_BUFFER)

    val loads = java.util.concurrent.CopyOnWriteArrayList<String>()
    val resizes = java.util.concurrent.CopyOnWriteArrayList<ResizeCall>()
    val focusChanges = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
    val mouseCalls = java.util.concurrent.CopyOnWriteArrayList<MouseCall>()
    val scrollCalls = java.util.concurrent.CopyOnWriteArrayList<ScrollCall>()
    val keyEvents = java.util.concurrent.CopyOnWriteArrayList<java.awt.event.KeyEvent>()
    val scripts = java.util.concurrent.CopyOnWriteArrayList<String>()
    val commands = java.util.concurrent.CopyOnWriteArrayList<String>()
    var closed = false
        private set
    var captureResult: ByteArray? = null

    private var version = 0L

    /** Publishes a solid-colour frame of the given physical size. */
    fun emitFrame(widthPx: Int, heightPx: Int, color: Color): BrowserFrame =
        BrowserFrame(solidBitmap(widthPx, heightPx, color), widthPx, heightPx, ++version).also { frame.value = it }

    fun emitPopup(xDip: Int, yDip: Int, widthDip: Int, heightDip: Int, density: Float, color: Color) {
        val widthPx = (widthDip * density).toInt()
        val heightPx = (heightDip * density).toInt()
        val popupFrame = BrowserFrame(solidBitmap(widthPx, heightPx, color), widthPx, heightPx, ++version)
        popup.value = PopupFrame(popupFrame, xDip, yDip, widthDip, heightDip)
    }

    fun emitEvent(event: PageEvent) = check(events.tryEmit(event))

    override fun load(url: String) {
        loads += url
    }

    override fun back() {
        commands += "back"
    }

    override fun forward() {
        commands += "forward"
    }

    override fun reload() {
        commands += "reload"
    }

    override fun stop() {
        commands += "stop"
    }

    override fun resize(widthDip: Int, heightDip: Int, density: Float) {
        resizes += ResizeCall(widthDip, heightDip, density)
    }

    override fun focus(focused: Boolean) {
        focusChanges += focused
    }

    val hiddenChanges = java.util.concurrent.CopyOnWriteArrayList<Boolean>()

    override fun setHidden(hidden: Boolean) {
        hiddenChanges += hidden
    }

    override fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int) {
        mouseCalls += MouseCall(awtId, xDip, yDip, button, clickCount, modifiers)
    }

    override fun scrollBy(xDip: Int, yDip: Int, pixels: Int) {
        scrollCalls += ScrollCall(xDip, yDip, pixels)
    }

    override fun key(event: java.awt.event.KeyEvent) {
        keyEvents += event
    }

    override fun runScript(js: String) {
        scripts += js
    }

    override fun capture(xDip: Int, yDip: Int, widthDip: Int, heightDip: Int): ByteArray? = captureResult

    var screenshotResult: PageShot? = null
    val screenshotCalls = java.util.concurrent.CopyOnWriteArrayList<ShotArea>()

    override fun screenshot(area: ShotArea, limits: ShotLimits): PageShot? {
        screenshotCalls += area
        return screenshotResult
    }

    var scroll: PageScroll? = PageScroll(0.0, 0.0)

    override fun scrollOffset(): PageScroll? = scroll

    override fun close() {
        closed = true
    }

    private companion object {
        const val EVENT_BUFFER = 64

        fun solidBitmap(widthPx: Int, heightPx: Int, color: Color): ImageBitmap {
            val bitmap = Bitmap()
            bitmap.allocPixels(ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB), widthPx, heightPx))
            bitmap.erase(color.toArgbInt())
            return bitmap.asComposeImageBitmap()
        }

        fun Color.toArgbInt(): Int =
            ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()
    }
}
