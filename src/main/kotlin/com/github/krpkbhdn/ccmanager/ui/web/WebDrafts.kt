package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebMarkShape
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebViewport
import com.github.krpkbhdn.ccmanager.web.StyleChange as StoredStyleChange
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WebDrafts::class.java)

/** The inspector draft of a [WebView]: live style preview, the element screenshot taken at pick time, and saving it as an item. */
internal class WebDrafts(
    private val service: WebReviewService,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    fun editStyle(view: WebView, property: String, value: String) {
        view.edits[property] = value
        val target = view.picked ?: return
        val changes = view.styleChanges
        view.host.runScript(if (changes.isEmpty()) InspectorBridge.clearPreview() else InspectorBridge.previewStyle(target.selector, changes.associate { it.property to it.to }))
    }

    fun cancel(view: WebView) {
        view.host.runScript(InspectorBridge.clearPreview())
        view.clearDraft()
    }

    fun editItem(view: WebView, item: WebItem) {
        view.beginEdit(item)
        view.selectedItemId = item.id()
    }

    /**
     * Adds the draft as a new item on the page it was picked on, or updates the item being edited. The draft is cleared before the call
     * and put back (unless a new one was started meanwhile) when it fails; a second Save while one runs is ignored.
     */
    fun save(view: WebView) {
        if (view.draftSaving) return
        val draft = view.snapshotDraft() ?: return
        view.draftSaving = true
        val changes = StyleDiff.changes(draft.target.computed, draft.edits).toStored()
        cancel(view)
        scope.launch {
            try {
                runGuarded({ message -> putBack(view, draft); reportError(message) }) {
                    if (draft.editing != null) {
                        io { service.updateItem(draft.editing.id(), draft.comment, changes) }
                    } else {
                        val viewport = draft.viewport ?: view.currentViewport()
                        val item = io { add(view, draft, changes, viewport) }
                        view.selectedItemId = item.id()
                        attachScreenshot(item, draft)
                    }
                }
            } finally {
                view.draftSaving = false
            }
        }
    }

    private fun add(view: WebView, draft: DraftSnapshot, changes: List<StoredStyleChange>, viewport: WebViewport?): WebItem =
        if (draft.shape == null) service.addItem(view.projectId, draft.pageUrl, draft.pageTitle, draft.target.toAnchor(), draft.comment, changes, viewport)
        else service.addItem(view.projectId, draft.pageUrl, draft.pageTitle, null, draft.comment, changes, viewport, draft.shape)

    private fun putBack(view: WebView, draft: DraftSnapshot) {
        if (view.picked != null) return
        view.restoreDraft(draft)
        val changes = view.styleChanges
        if (changes.isNotEmpty()) view.host.runScript(InspectorBridge.previewStyle(draft.target.selector, changes.associate { it.property to it.to }))
    }

    /** Taken when the element is picked, so it matches what the user saw even if the page scrolls before saving. */
    fun captureAtPick(view: WebView, target: ElementTarget) {
        view.pickViewport = view.currentViewport()
        capture(view, target.viewportRect ?: return)
    }

    /** Starts a draft for a mark just drawn: the comment popover opens at [popoverAt] (canvas px) and the area around the mark is captured. */
    fun beginShape(view: WebView, shape: WebMarkShape, popoverAt: Offset) {
        view.beginDraft(shapeTarget(shape))
        view.draftShape = shape
        view.pickViewport = view.currentViewport()
        view.contextMenu = ContextMenuState(0.0, 0.0, popoverAt, emptyList(), feedback = true)
        val (area, crop) = cropOf(view, shape) ?: return
        view.shapeCrop = crop
        capture(view, area)
    }

    /** The viewport area around [shape] and the document rectangle it shows, or null when the shape is not on screen. */
    private fun cropOf(view: WebView, shape: WebMarkShape): Pair<Rect, Rect>? {
        val scroll = view.scroll ?: return null
        val layout = view.layout ?: return null
        val area = cropAround(shape, scroll, layout.widthCss.toDouble(), layout.heightCss.toDouble()) ?: return null
        return area to Rect(area.x + scroll.x, area.y + scroll.y, area.w, area.h)
    }

    /** A moved shape has lost its stored screenshot: captures the area around it again and stores it with the mark; best effort. */
    suspend fun refreshScreenshot(view: WebView, itemId: Long, shape: WebMarkShape) {
        val (area, crop) = cropOf(view, shape) ?: return
        try {
            io {
                val png = grab(view, area) ?: return@io
                annotated(shape, crop, png)?.let { service.saveAnnotatedScreenshot(itemId, png, it) }
            }
        } catch (e: RuntimeException) {
            log.warn("Refreshing the shape screenshot failed: {}", e.javaClass.simpleName)
        }
    }

    private fun grab(view: WebView, area: Rect): ByteArray? =
        view.host.capture(area.x.roundToInt(), area.y.roundToInt(), max(1, ceil(area.w).toInt()), max(1, ceil(area.h).toInt()))

    private fun capture(view: WebView, area: Rect) {
        val generation = view.draftGeneration
        scope.launch {
            val png = try {
                io { grab(view, area) }
            } catch (e: RuntimeException) {
                log.warn("Element screenshot failed: {}", e.javaClass.simpleName)
                null
            }
            if (view.draftGeneration == generation) view.pickShot = png
        }
    }

    private suspend fun attachScreenshot(item: WebItem, draft: DraftSnapshot) {
        val png = draft.shot ?: return
        try {
            io {
                val mark = draft.shape?.let { annotated(it, draft.crop, png) }
                if (mark != null) service.saveAnnotatedScreenshot(item.id(), png, mark) else service.saveScreenshot(item.id(), png)
            }
        } catch (e: RuntimeException) {
            log.warn("Saving the element screenshot failed: {}", e.message)
        }
    }

    private fun annotated(shape: WebMarkShape, crop: Rect?, png: ByteArray): WebMarkShape? {
        val area = crop?.takeIf { it.w > 0 } ?: return null
        val width = ImageIO.read(ByteArrayInputStream(png))?.width ?: return null
        return shape.inCrop(area, width / area.w)
    }

    private fun shapeTarget(shape: WebMarkShape) = ElementTarget(
        selector = "", xpath = "", tag = "", id = "", classes = emptyList(), text = "", outerHtml = "",
        rect = Rect(shape.x, shape.y, shape.w, shape.h), viewportRect = null, computed = emptyMap(), box = null, sourceHint = null, frame = null, pageUrl = null,
    )
}
