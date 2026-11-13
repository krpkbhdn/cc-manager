package com.github.krpkbhdn.ccmanager.ui.web.browser

import com.github.krpkbhdn.ccmanager.ui.web.ConsoleLevel
import com.github.krpkbhdn.ccmanager.ui.web.InspectorBridge
import com.github.krpkbhdn.ccmanager.ui.web.InspectorMessage
import com.github.krpkbhdn.ccmanager.web.WebReviewProperties
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.cef.CefApp
import org.slf4j.LoggerFactory
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefDragData
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefScreenInfo
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.Canvas
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A windowless Chromium page behind [WebPageHost]. CEF calls the handlers on its own threads and they only write
 * flows; sizes in the interface are DIPs, the painted frame is DIP size times the density.
 * Per host a client and message router, so a query always belongs to its page.
 */
class JcefPageHost(app: CefApp, url: String = "about:blank", private val screenshotTimeout: Duration = WebReviewProperties.DEFAULT_SCREENSHOT_TIMEOUT) : WebPageHost {
    // First, so a rejected URL fails before any native object exists.
    private val initialUrl = NavigationPolicy.require(url)
    private val pageState = MutableStateFlow(PageState(url = initialUrl))
    private val frameState = MutableStateFlow<BrowserFrame?>(null)
    private val popupState = MutableStateFlow<PopupFrame?>(null)
    private val cursorState = MutableStateFlow(java.awt.Cursor.DEFAULT_CURSOR)
    private val eventFlow = MutableSharedFlow<PageEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val page: StateFlow<PageState> = pageState.asStateFlow()
    override val frame: StateFlow<BrowserFrame?> = frameState.asStateFlow()
    override val popup: StateFlow<PopupFrame?> = popupState.asStateFlow()
    override val cursor: StateFlow<Int> = cursorState.asStateFlow()
    override val events: SharedFlow<PageEvent> = eventFlow.asSharedFlow()

    @Volatile private var viewWidth = DEFAULT_WIDTH
    @Volatile private var viewHeight = DEFAULT_HEIGHT
    @Volatile private var density = 1.0
    private val frameLock = Any()
    @Volatile private var popupRect = Rectangle()
    private val pageFrames = FrameBuffer()
    private val popupFrames = FrameBuffer()
    private val pagePublisher = FramePublisher(pageFrames, ::publishIfCurrent, paintScheduler)
    private val consoleLimit = PerSecondLimit(CONSOLE_PER_SECOND)
    private val inspectorLimit = PerSecondLimit(INSPECTOR_PER_SECOND)
    private val hoverLimit = PerSecondLimit(HOVER_PER_SECOND)
    private val closed = AtomicBoolean()
    private val loadLock = Any()
    private var created = false
    private var pendingUrl: String? = null
    private val userNavigationDeadline = AtomicLong(NO_DEADLINE)

    /** Redirects of a navigation the user typed are part of it; this lasts until that load ends or the same window as the mark runs out. */
    @Volatile private var userRedirectDeadline = NO_DEADLINE
    private val userRedirectChain get() = userRedirectDeadline != NO_DEADLINE && System.nanoTime() - userRedirectDeadline < 0

    private val eventSource = Canvas()
    private val client: CefClient = app.createClient()
    private val router = CefMessageRouter.create()
    private val browser: CefBrowser

    init {
        router.addHandler(inspectorQueries(), true)
        client.addMessageRouter(router)
        client.addDisplayHandler(displayHandler())
        client.addLoadHandler(loadHandler())
        client.addRequestHandler(navigationGuard())
        client.addLifeSpanHandler(lifeSpanHandler())
        JcefLockdown.install(client)
        pendingUrl = initialUrl.takeUnless { it == BLANK }
        browser = client.createBrowser(BLANK, CefRendering.CefRenderingWithHandler(renderHandler(), eventSource), false)
        browser.createImmediately()
    }

    /** A load before the native browser exists is silently dropped by CEF, so it waits for onAfterCreated. */
    override fun load(url: String) {
        val allowed = NavigationPolicy.require(url)
        markUserNavigation()
        synchronized(loadLock) {
            if (created) browser.loadURL(allowed) else pendingUrl = allowed
        }
    }

    override fun back() = browser.also { markUserNavigation() }.goBack()
    override fun forward() = browser.also { markUserNavigation() }.goForward()
    override fun reload() = browser.also { markUserNavigation() }.reload()
    /** A same-document history step may fire no load event, so the mark expires on its own. */
    private fun markUserNavigation() = userNavigationDeadline.set(System.nanoTime() + USER_NAVIGATION_WINDOW_NANOS)

    private fun consumeUserNavigation(): Boolean {
        val deadline = userNavigationDeadline.getAndSet(NO_DEADLINE)
        return deadline != NO_DEADLINE && System.nanoTime() - deadline < 0
    }

    override fun stop() = browser.stopLoad()
    override fun focus(focused: Boolean) = browser.setFocus(focused)

    override fun setHidden(hidden: Boolean) {
        if (closed.get()) return
        pagePublisher.hidden = hidden
        // This JCEF has no wasHidden(); the lowest paint rate is the cheapest way to idle the renderer.
        browser.setWindowlessFrameRate(if (hidden) HIDDEN_FRAME_RATE else VISIBLE_FRAME_RATE)
        if (!hidden) browser.invalidate()
    }
    override fun runScript(js: String) = browser.executeJavaScript(js, pageState.value.url, 0)
    override fun key(event: KeyEvent) = browser.sendKeyEvent(event)

    /** A frame painted for the previous size must not be shown at the new one; CEF repaints after [resize], so it is dropped. */
    private fun publishIfCurrent(frame: BrowserFrame) = synchronized(frameLock) {
        // A dropped frame also clears the shown one: the buffer has moved on and must not be able to close an image still drawn.
        frameState.value = frame.takeIf { frameFits(it.widthPx, it.heightPx, viewWidth, viewHeight, density) }
    }

    override fun resize(widthDip: Int, heightDip: Int, density: Float) {
        var scaleChanged = false
        var sizeChanged = false
        synchronized(frameLock) {
            scaleChanged = density.toDouble() != this.density
            sizeChanged = scaleChanged || widthDip.coerceAtLeast(1) != viewWidth || heightDip.coerceAtLeast(1) != viewHeight
            viewWidth = widthDip.coerceAtLeast(1)
            viewHeight = heightDip.coerceAtLeast(1)
            this.density = density.toDouble()
            if (sizeChanged) frameState.value = null
        }
        if (scaleChanged) browser.notifyScreenInfoChanged()
        browser.wasResized(viewWidth, viewHeight)
        // Windowless CEF may not repaint a resized view until an input event.
        if (sizeChanged) browser.invalidate()
    }

    override fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int) {
        browser.sendMouseEvent(MouseEvent(eventSource, awtId, System.currentTimeMillis(), modifiers, xDip, yDip, clickCount, false, button))
    }

    /** Measured: CEF scrolls rotation * scrollAmount / 1.2 px, so scrollAmount 1 and rotation = pixels * 1.2 gives exact pixels. */
    override fun scrollBy(xDip: Int, yDip: Int, pixels: Int) {
        val rotation = Math.round(pixels * WHEEL_UNITS_PER_PIXEL).toInt()
        browser.sendMouseWheelEvent(
            MouseWheelEvent(eventSource, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, xDip, yDip, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation),
        )
    }

    override fun capture(xDip: Int, yDip: Int, widthDip: Int, heightDip: Int): ByteArray? =
        pageFrames.withLatestImage { image, frameDensity -> FrameCrop.png(image, frameDensity, xDip, yDip, widthDip, heightDip) }

    override fun screenshot(area: ShotArea, limits: ShotLimits): PageShot? = if (closed.get()) null else shooter().screenshot(area, limits)

    override fun scrollOffset(): PageScroll? = if (closed.get()) null else shooter().scrollOffset()

    private fun shooter() = PageShooter(DevTools(browser), screenshotTimeout) { pageState.value }

    /** Open Skia images behind the page frames, for tests that watch native memory. */
    internal val openFrameImages: Int get() = pageFrames.openImages

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pagePublisher.hidden = true
        client.removeMessageRouter(router)
        router.dispose()
        browser.close(true)
        client.dispose()
        pagePublisher.close()
        popupFrames.close()
    }

    private fun emit(event: PageEvent) {
        eventFlow.tryEmit(event)
    }

    private fun inspectorQueries() = object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(b: CefBrowser, f: CefFrame, id: Long, request: String, persistent: Boolean, cb: CefQueryCallback): Boolean {
            // Subframes run foreign pages: only the main frame may talk to the app.
            if (f.isMain && request.length <= InspectorMessage.MAX_MESSAGE_CHARS && withinInspectorBudget(request)) emit(PageEvent.Inspector(request))
            cb.success("")
            return true
        }
    }

    /** Hover is chatty and has a budget of its own, so it can never use up the budget of picks and clicks. */
    private fun withinInspectorBudget(json: String) =
        if (json.startsWith(HOVER_PREFIX)) hoverLimit.tryAcquire() else inspectorLimit.tryAcquire()

    private fun displayHandler() = object : CefDisplayHandlerAdapter() {
        override fun onTitleChange(b: CefBrowser, title: String) = pageState.update { it.copy(title = title) }
        override fun onAddressChange(b: CefBrowser, f: CefFrame, url: String) {
            if (f.isMain) pageState.update { it.copy(url = url) }
        }

        override fun onConsoleMessage(b: CefBrowser, level: CefSettings.LogSeverity, message: String, source: String, line: Int): Boolean {
            if (consoleLimit.tryAcquire()) emit(PageEvent.Console(consoleLevel(level), message.take(MAX_CONSOLE_CHARS), source, line))
            return false
        }
    }

    private fun loadHandler() = object : CefLoadHandlerAdapter() {
        override fun onLoadingStateChange(b: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) =
            pageState.update { it.copy(loading = isLoading, canGoBack = canGoBack, canGoForward = canGoForward) }

        override fun onLoadEnd(b: CefBrowser, f: CefFrame, httpStatusCode: Int) {
            if (!f.isMain) return
            userNavigationDeadline.set(NO_DEADLINE)
            userRedirectDeadline = NO_DEADLINE
            f.executeJavaScript(INSPECTOR_BOOTSTRAP, f.url, 0)
        }

        override fun onLoadError(b: CefBrowser, f: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
            if (f.isMain) {
                userNavigationDeadline.set(NO_DEADLINE)
                userRedirectDeadline = NO_DEADLINE
            }
            if (f.isMain && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) emit(PageEvent.LoadFailed(failedUrl, errorText))
        }
    }

    /** What the user asked for (address bar, back, reload) follows the typed-URL rules; whatever the page does itself is stricter. */
    private fun navigationGuard() = object : CefRequestHandlerAdapter() {
        override fun onBeforeBrowse(b: CefBrowser, f: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
            val url = request.url
            if (f.isMain && !isRedirect && consumeUserNavigation()) userRedirectDeadline = System.nanoTime() + USER_NAVIGATION_WINDOW_NANOS
            val allowed = if (userRedirectChain && f.isMain) NavigationPolicy.isAllowed(url) else NavigationPolicy.isAllowedFromPage(url, pageState.value.url, f.isMain)
            if (!allowed) reportBlocked(url)
            return !allowed
        }
    }

    /** target=_blank and window.open stay in the same view. */
    private fun lifeSpanHandler() = object : CefLifeSpanHandlerAdapter() {
        override fun onAfterCreated(b: CefBrowser) {
            synchronized(loadLock) {
                created = true
                pendingUrl?.let { b.loadURL(it) }
                pendingUrl = null
            }
        }

        override fun onBeforePopup(b: CefBrowser, f: CefFrame, targetUrl: String, targetFrameName: String): Boolean {
            if (NavigationPolicy.isAllowedFromPage(targetUrl, pageState.value.url)) b.loadURL(targetUrl)
            else reportBlocked(targetUrl)
            return true
        }
    }

    /** Logs and shows only scheme and host, never the path or query the page may have put data into. */
    private fun reportBlocked(url: String) {
        val where = NavigationPolicy.originOf(url) ?: "a ${NavigationPolicy.schemeOf(url)} URL"
        log.warn("Blocked navigation to {}", where)
        emit(PageEvent.LoadFailed(where, "blocked: a page may only open its own origin or the local network"))
    }

    private fun renderHandler() = object : CefRenderHandler {
        override fun getViewRect(b: CefBrowser) = Rectangle(0, 0, viewWidth, viewHeight)
        override fun getScreenInfo(b: CefBrowser, info: CefScreenInfo): Boolean {
            val rect = Rectangle(0, 0, viewWidth, viewHeight)
            info.Set(density, SCREEN_DEPTH, SCREEN_DEPTH_PER_COMPONENT, false, rect, rect)
            return true
        }

        override fun getScreenPoint(b: CefBrowser, p: Point) = Point(p)
        override fun getDeviceScaleFactor(b: CefBrowser) = density
        override fun onPopupShow(b: CefBrowser, show: Boolean) {
            if (!show) popupState.value = null
        }

        override fun onPopupSize(b: CefBrowser, size: Rectangle) {
            popupRect = Rectangle(size)
        }

        override fun onPaint(b: CefBrowser, popup: Boolean, dirty: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
            if (popup) {
                val r = popupRect
                popupFrames.publish(buffer, width, height)?.let { popupState.value = PopupFrame(it, r.x, r.y, r.width, r.height) }
            } else {
                pagePublisher.onPaint(buffer, width, height, density)
            }
        }

        override fun onCursorChange(b: CefBrowser, type: Int): Boolean {
            cursorState.value = type
            return true
        }

        override fun startDragging(b: CefBrowser, data: CefDragData, mask: Int, x: Int, y: Int) = false
        override fun updateDragCursor(b: CefBrowser, operation: Int) {}
    }

    companion object {
        private val log = LoggerFactory.getLogger(JcefPageHost::class.java)
        private const val NO_DEADLINE = 0L
        private val USER_NAVIGATION_WINDOW_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        private const val BLANK = "about:blank"
        private const val HIDDEN_FRAME_RATE = 1
        private const val VISIBLE_FRAME_RATE = 60
        private const val HOVER_PREFIX = "{\"type\":\"hover\""
        private const val CONSOLE_PER_SECOND = 200
        private const val INSPECTOR_PER_SECOND = 100
        private const val HOVER_PER_SECOND = 40
        private const val EVENT_BUFFER = 1024
        private const val DEFAULT_WIDTH = 800
        private const val DEFAULT_HEIGHT = 600
        private const val SCREEN_DEPTH = 32
        private const val SCREEN_DEPTH_PER_COMPONENT = 8
        private const val MAX_CONSOLE_CHARS = 8_000
        private const val WHEEL_UNITS_PER_PIXEL = 1.2
        private val INSPECTOR_RESOURCES = listOf("00-core", "10-hover", "20-pins", "30-spacing", "40-audit", "50-similar", "60-contrast", "70-stack", "99-api")
            .map { "/web-review/inspector/$it.js" }

        private val paintScheduler: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { Thread(it, "web-frame-publisher").apply { isDaemon = true } }
        }

        private val INSPECTOR_BOOTSTRAP: String by lazy {
            InspectorBridge.bootstrapScript(
                INSPECTOR_RESOURCES.map { resource ->
                    JcefPageHost::class.java.getResourceAsStream(resource)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Missing resource $resource")
                },
            )
        }

        /** Starts CEF on first use; [dataDir] is the app data directory that holds the browser cache. */
        fun open(dataDir: Path, url: String = "about:blank", screenshotTimeout: Duration = WebReviewProperties.DEFAULT_SCREENSHOT_TIMEOUT): WebPageHost =
            JcefPageHost(JcefRuntime.start(dataDir), url, screenshotTimeout)

        internal fun consoleLevel(severity: CefSettings.LogSeverity): ConsoleLevel = when (severity) {
            CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> ConsoleLevel.DEBUG
            CefSettings.LogSeverity.LOGSEVERITY_WARNING -> ConsoleLevel.WARN
            CefSettings.LogSeverity.LOGSEVERITY_ERROR, CefSettings.LogSeverity.LOGSEVERITY_FATAL -> ConsoleLevel.ERROR
            else -> ConsoleLevel.LOG
        }
    }
}
