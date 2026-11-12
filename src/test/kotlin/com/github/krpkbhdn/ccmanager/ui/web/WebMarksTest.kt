package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.review.ReviewTool
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageState
import com.github.krpkbhdn.ccmanager.web.BrowserLimits
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebMarkShape
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebViewport
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val URL = "http://localhost:3000/app"

class WebMarksTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(WebReviewService::class.java)
    private val host = FakePageHost()
    private val errors = CopyOnWriteArrayList<String>()
    private val state = WebState(service, { host }, { _, _ -> }, { _, _, _, _ -> }, { LiveSessions() }, scope, { errors += it })

    init {
        `when`(service.subscribe(any())).thenReturn(Runnable { })
        `when`(service.browserLimits()).thenReturn(BrowserLimits(3, Duration.ofMinutes(10)))
    }

    @AfterEach
    fun stop() {
        state.close()
        scope.cancel()
    }

    private val rectangle = WebMarkShape(MarkKind.RECTANGLE, 100.0, 200.0, 200.0, 200.0)
    private val rectangleMark = MarkDraft(MarkKind.RECTANGLE, 0.1, 0.1, 0.2, 0.1, "")

    private fun markItem(id: Long, viewport: WebViewport? = null, status: WebItemStatus = WebItemStatus.OPEN) =
        WebItem(id, 1, 1, id.toInt(), ElementAnchor("", null, null, null, ElementAnchor.Rect(100.0, 200.0, 200.0, 200.0), null, null, null, null), "c$id", emptyList(), status, null, viewport, rectangle)

    private fun openView(vararg items: WebItem): WebView {
        `when`(service.items(1, URL)).thenReturn(items.toList())
        val view = state.view(1)
        await { host.events.subscriptionCount.value > 0 }
        host.page.value = PageState(url = URL)
        host.emitEvent(PageEvent.Inspector("""{"type":"ready","url":"$URL","title":"App"}"""))
        await { view.pageItems.size == items.size && view.pageKey != null }
        view.layout = PageViewport(1000, 700)
        view.scroll = DocScroll(0.0, 100.0, 1000.0, 2000.0)
        view.markSpace = WebMarkSpace({ view.layout }, { view.scroll }, { 1f }, { _, _ -> }, { _, _ -> })
        return view
    }

    private fun png(width: Int, height: Int) = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun `a finished mark starts a shape draft with the comment popover open`() {
        val view = openView()

        state.addMark(view, rectangleMark)

        assertThat(view.draftShape).isEqualTo(rectangle)
        assertThat(view.picked?.rect).isEqualTo(Rect(100.0, 200.0, 200.0, 200.0))
        assertThat(view.contextMenu?.feedback).isTrue()
        assertThat(view.hotKeyModes().gestureRunning).isTrue()
    }

    @Test
    fun `a mark is refused while another draft holds typed work`() {
        val view = openView()
        view.comment.setTextAndPlaceCursorAtEnd("typed")

        state.addMark(view, rectangleMark)

        assertThat(view.draftShape).isNull()
        assertThat(errors).containsExactly(DRAFT_FIRST_NOTICE)
    }

    @Test
    fun `saving a shape draft adds an item with the shape and no element anchor`() {
        `when`(service.addItem(anyLong(), anyString(), anyString(), isNull(), anyString(), anyList(), any(), any())).thenReturn(markItem(7))
        val view = openView()
        state.addMark(view, rectangleMark)
        view.comment.setTextAndPlaceCursorAtEnd("move this")

        state.saveDraft(view)

        verify(service, timeout(3000)).addItem(eq(1L), eq(URL), eq("App"), isNull(), eq("move this"), eq(emptyList()), any(), eq(rectangle))
        await { view.draftShape == null && view.picked == null }
        assertThat(errors).isEmpty()
    }

    @Test
    fun `the screenshot around the shape is saved with the mark in crop pixels`() {
        val shot = png(496, 496)
        host.captureResult = shot
        `when`(service.addItem(anyLong(), anyString(), anyString(), isNull(), anyString(), anyList(), any(), any())).thenReturn(markItem(7))
        val view = openView()
        state.addMark(view, rectangleMark)
        await { view.pickShot != null }
        view.comment.setTextAndPlaceCursorAtEnd("move this")

        state.saveDraft(view)

        verify(service, timeout(3000)).saveAnnotatedScreenshot(7, shot, WebMarkShape(MarkKind.RECTANGLE, 48.0, 48.0, 400.0, 400.0))
    }

    @Test
    fun `cancelling the draft drops the shape`() {
        val view = openView()
        state.addMark(view, rectangleMark)

        state.cancelDraft(view)

        assertThat(view.draftShape).isNull()
        assertThat(view.picked).isNull()
    }

    @Test
    fun `leaving drawing mode keeps the finished shape so its comment can still be saved`() {
        val view = openView()
        state.perform(view, WebAction.ToggleDrawing)
        state.addMark(view, rectangleMark)

        state.perform(view, WebAction.ToggleDrawing)

        assertThat(view.draftShape).isEqualTo(rectangle)
    }

    @Test
    fun `closing a placed polygon finishes a freeform shape`() {
        val view = openView()
        listOf(0.1 to 0.1, 0.3 to 0.1, 0.2 to 0.3).forEach { (x, y) -> view.polygon.add(x, y, 1000f, 2000f, 1f) }

        assertThat(state.closePolygon(view)).isTrue()

        assertThat(view.draftShape?.kind).isEqualTo(MarkKind.FREEFORM)
        assertThat(view.draftShape?.points).hasSize(3)
        assertThat(view.polygon.isEmpty).isTrue()
    }

    @Test
    fun `a polygon with too few points does not close`() {
        val view = openView()
        view.polygon.add(0.1, 0.1, 1000f, 2000f, 1f)

        assertThat(state.closePolygon(view)).isFalse()

        assertThat(view.polygon.points).containsExactly(MarkPoint(0.1, 0.1))
    }

    @Test
    fun `cancelling the gesture drops a half placed polygon and then a pending shape`() {
        val view = openView()
        view.polygon.add(0.1, 0.1, 1000f, 2000f, 1f)
        state.perform(view, WebAction.CancelGesture)
        assertThat(view.polygon.isEmpty).isTrue()

        state.addMark(view, rectangleMark)
        state.perform(view, WebAction.CancelGesture)
        assertThat(view.draftShape).isNull()
    }

    @Test
    fun `moving a mark shows it at once and saves the last position once after a pause`() {
        val item = markItem(7)
        val view = openView(item)
        val first = MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, "")
        val last = MarkDraft(MarkKind.RECTANGLE, 0.3, 0.1, 0.2, 0.1, "")

        state.editMark(view, item, first)
        state.editMark(view, item, last)

        assertThat(view.pendingMarks[7L]).isEqualTo(last)
        verify(service, timeout(3000)).tryUpdateItemShape(7, WebMarkShape(MarkKind.RECTANGLE, 300.0, 200.0, 200.0, 200.0))
        verify(service, never()).tryUpdateItemShape(7, WebMarkShape(MarkKind.RECTANGLE, 200.0, 200.0, 200.0, 200.0))
    }

    @Test
    fun `a failed save reports the error and drops the unsaved geometry`() {
        val item = markItem(7)
        doThrow(IllegalStateException("Only an open item can be edited.")).`when`(service).tryUpdateItemShape(eq(7L), any())
        val view = openView(item)

        state.editMark(view, item, MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, ""))

        await { errors.isNotEmpty() }
        assertThat(view.pendingMarks).isEmpty()
    }

    @Test
    fun `delete removes the selected visible mark only`() {
        val view = openView(markItem(7), markItem(8))
        view.selectedItemId = 7

        state.perform(view, WebAction.DeleteMark)

        verify(service, timeout(3000)).deleteItem(7)
        verify(service, never()).deleteItem(8)
        assertThat(view.selectedItemId).isNull()
    }

    @Test
    fun `delete never removes a mark hidden by the device scope`() {
        val view = openView(markItem(7, WebViewport(1280, 720, null, DeviceCategory.DESKTOP)))
        view.device = DeviceSelection.of(DeviceSelection.presets.first { it.name == "iPhone 14" })
        view.selectedItemId = 7

        state.perform(view, WebAction.DeleteMark)

        verify(service, never()).deleteItem(anyLong())
        assertThat(view.selectedItemId).isEqualTo(7L)
    }

    @Test
    fun `drawn marks are kept apart from the pins and follow the device scope`() {
        val pin = WebItem(1, 1, 1, 1, ElementAnchor("a", null, null, null, null, null, null, null, null), "p", emptyList(), WebItemStatus.OPEN, null, null)
        val desktop = markItem(2, WebViewport(1280, 720, null, DeviceCategory.DESKTOP))
        val mobile = markItem(3, WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE))
        val view = openView(pin, desktop, mobile)

        assertThat(view.visibleItems).containsExactly(pin)
        assertThat(view.visibleMarks).containsExactly(desktop, mobile)

        view.device = DeviceSelection.of(DeviceSelection.presets.first { it.name == "iPhone 14" })

        assertThat(view.visibleMarks).containsExactly(mobile)
    }

    @Test
    fun `go to scrolls the page to the shape`() {
        val item = markItem(7)
        val view = openView(item)

        state.selectItem(view, item)

        assertThat(view.selectedItemId).isEqualTo(7L)
        assertThat(host.scripts.last()).contains("scrollBy").contains("-300").contains("-150")
    }

    @Test
    fun `the tool selection is sticky across marks`() {
        val view = openView()
        state.perform(view, WebAction.SelectTool(ReviewTool.RECTANGLE))

        state.addMark(view, rectangleMark)

        assertThat(view.drawTool).isEqualTo(ReviewTool.RECTANGLE)
    }

    @Test
    fun `a mark that vanished or was sent meanwhile drops its unsaved geometry without an error`() {
        val item = markItem(7)
        val view = openView(item)

        state.editMark(view, item, MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, ""))

        verify(service, timeout(3000)).tryUpdateItemShape(eq(7L), any())
        await { view.pendingMarks.isEmpty() }
        assertThat(errors).isEmpty()
    }

    @Test
    fun `a saved move stores a fresh screenshot of the new shape`() {
        `when`(service.tryUpdateItemShape(eq(7L), any())).thenReturn(true)
        host.captureResult = png(496, 496)
        val item = markItem(7)
        val view = openView(item)

        state.editMark(view, item, MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, ""))

        verify(service, timeout(3000)).saveAnnotatedScreenshot(eq(7L), any(), any())
    }

    @Test
    fun `deleting a mark right after a drag cancels its pending save`() {
        val item = markItem(7)
        val view = openView(item)
        view.selectedItemId = 7
        state.editMark(view, item, MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, ""))

        state.perform(view, WebAction.DeleteMark)
        Thread.sleep(900)

        verify(service, timeout(3000)).deleteItem(7)
        verify(service, never()).tryUpdateItemShape(anyLong(), any())
        assertThat(errors).isEmpty()
    }

    @Test
    fun `only an open mark can be moved or deleted`() {
        val sent = markItem(7, status = WebItemStatus.SENT)
        val view = openView(sent)
        view.selectedItemId = 7

        state.editMark(view, sent, MarkDraft(MarkKind.RECTANGLE, 0.2, 0.1, 0.2, 0.1, ""))
        state.perform(view, WebAction.DeleteMark)

        assertThat(view.pendingMarks).isEmpty()
        verify(service, never()).deleteItem(anyLong())
        assertThat(view.selectedItemId).isEqualTo(7L)
    }

    @Test
    fun `the comment popover sits at the shape corner, or the layout centre when the mark space is unknown`() {
        val view = openView()
        state.addMark(view, rectangleMark)
        assertThat(view.contextMenu?.canvasOffset).isEqualTo(Offset(300f, 300f))

        state.cancelDraft(view)
        view.markSpace = null
        state.addMark(view, rectangleMark)
        assertThat(view.contextMenu?.canvasOffset).isEqualTo(Offset(500f, 350f))
    }
}
