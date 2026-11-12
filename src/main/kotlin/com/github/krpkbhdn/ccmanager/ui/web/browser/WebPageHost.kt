package com.github.krpkbhdn.ccmanager.ui.web.browser

import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.ui.web.ConsoleLevel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Latest painted frame; `version` increases on every paint so Compose redraws. Size in physical px. */
class BrowserFrame(val bitmap: ImageBitmap, val widthPx: Int, val heightPx: Int, val version: Long, val density: Double = 1.0)

/** A <select> popup layer: frame plus its rectangle in DIPs of the page view. */
class PopupFrame(val frame: BrowserFrame, val xDip: Int, val yDip: Int, val widthDip: Int, val heightDip: Int)

data class PageState(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

sealed interface PageEvent {
    data class Console(val level: ConsoleLevel, val text: String, val source: String, val line: Int) : PageEvent

    /** Raw JSON string sent by inspector.js through window.__ccmHost (main frame only, size-capped). */
    data class Inspector(val json: String) : PageEvent

    data class LoadFailed(val url: String, val error: String) : PageEvent
}

interface WebPageHost : AutoCloseable {
    val page: StateFlow<PageState>
    val frame: StateFlow<BrowserFrame?>
    val popup: StateFlow<PopupFrame?>

    /** java.awt.Cursor predefined-cursor type constant of the page's current cursor. */
    val cursor: StateFlow<Int>
    val events: SharedFlow<PageEvent>

    fun load(url: String)
    fun back()
    fun forward()
    fun reload()
    fun stop()
    fun resize(widthDip: Int, heightDip: Int, density: Float)
    fun focus(focused: Boolean)

    /** A hidden page stops painting and publishing frames; showing it again forces a fresh frame. */
    fun setHidden(hidden: Boolean)

    /** awtId = java.awt.event.MouseEvent id (PRESSED/RELEASED/MOVED/DRAGGED/ENTERED/EXITED), coordinates in DIPs. */
    fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int)

    /** Scroll by `pixels` page pixels at the point (positive = down). */
    fun scrollBy(xDip: Int, yDip: Int, pixels: Int)

    /** Forward the ORIGINAL AWT KeyEvent (rebuilt events break on Windows). */
    fun key(event: java.awt.event.KeyEvent)
    fun runScript(js: String)

    /** PNG of a rectangle (DIPs) cropped from the last frame, or null if no frame/rect outside. */
    fun capture(xDip: Int, yDip: Int, widthDip: Int, heightDip: Int): ByteArray?
    override fun close()
}
