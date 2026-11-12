package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.review.ReviewTool
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageState
import com.github.krpkbhdn.ccmanager.web.BrowserLimits
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private const val WIDTH = 800
private const val HEIGHT = 600
private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 10

/** The mark layer sits above the canvas in the same box, so these check who gets the pointer: the layer in drawing mode, the canvas otherwise. */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class WebMarkLayerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(WebReviewService::class.java)
    private val host = FakePageHost()
    private val state = WebState(service, { host }, { _, _ -> }, { _, _, _, _ -> }, { LiveSessions() }, scope, { })
    private var scene: ImageComposeScene? = null
    private var nanos = 0L

    init {
        `when`(service.subscribe(any())).thenReturn(Runnable { })
        `when`(service.browserLimits()).thenReturn(BrowserLimits(3, Duration.ofMinutes(10)))
    }

    @AfterEach
    fun stop() {
        scene?.close()
        state.close()
        scope.cancel()
    }

    private fun show(configure: (WebView) -> Unit): WebView {
        val view = state.view(1)
        await { host.events.subscriptionCount.value > 0 }
        host.page.value = PageState(url = "http://localhost:3000/app")
        host.emitEvent(PageEvent.Inspector("""{"type":"ready","url":"http://localhost:3000/app","title":"App"}"""))
        await { view.pageKey != null }
        view.scroll = DocScroll(0.0, 0.0, WIDTH.toDouble(), HEIGHT.toDouble())
        configure(view)
        val viewport = PageViewport(WIDTH, HEIGHT)
        scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            Box(Modifier.fillMaxSize()) {
                WebCanvas(host, viewport, Modifier.fillMaxSize(), interaction = view.interaction)
                WebPageOverlay(view, state, viewport)
            }
        }.also { frames(it) }
        return view
    }

    private fun frames(scene: ImageComposeScene) = repeat(SETTLE_FRAMES) { scene.render(++nanos * FRAME_NANOS) }

    private fun drag(from: Offset, to: Offset) {
        val scene = scene!!
        scene.sendPointerEvent(PointerEventType.Move, from)
        scene.sendPointerEvent(PointerEventType.Press, from, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Move, Offset((from.x + to.x) / 2, (from.y + to.y) / 2), buttons = PointerButtons(isPrimaryPressed = true))
        scene.sendPointerEvent(PointerEventType.Move, to, buttons = PointerButtons(isPrimaryPressed = true))
        scene.sendPointerEvent(PointerEventType.Release, to, button = PointerButton.Primary)
        frames(scene)
    }

    @Test
    fun `in drawing mode a drag draws a rectangle and the page gets no mouse`() {
        val view = show { it.drawMode = true; it.drawTool = ReviewTool.RECTANGLE }

        drag(Offset(200f, 150f), Offset(400f, 300f))

        await { view.draftShape != null }
        assertThat(view.draftShape?.kind).isEqualTo(MarkKind.RECTANGLE)
        assertThat(view.draftShape?.x).isEqualTo(200.0)
        assertThat(view.draftShape?.w).isEqualTo(200.0)
        assertThat(host.mouseCalls).isEmpty()
    }

    @Test
    fun `outside drawing mode the page keeps the pointer and no mark is drawn`() {
        val view = show { it.drawTool = ReviewTool.RECTANGLE }

        drag(Offset(200f, 150f), Offset(400f, 300f))

        await { host.mouseCalls.isNotEmpty() }
        assertThat(view.draftShape).isNull()
    }

    @Test
    fun `in drawing mode the wheel scrolls the page through the inspector`() {
        show { it.drawMode = true }

        scene!!.sendPointerEvent(PointerEventType.Scroll, Offset(300f, 300f), scrollDelta = Offset(0f, 2f))
        frames(scene!!)

        assertThat(host.scripts.last()).contains("scrollBy").contains("200")
        assertThat(host.scrollCalls).isEmpty()
    }
}
