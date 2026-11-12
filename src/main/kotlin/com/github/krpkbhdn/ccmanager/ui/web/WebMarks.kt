package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebMarkShape
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val SAVE_DEBOUNCE_MILLIS = 400L
// How long an unsaved-geometry override outlives its save: the stored items reload shortly after, and a doc size change may keep them apart for good.
private const val PENDING_GRACE_MILLIS = 3_000L
private const val TOO_MANY_POINTS = "A freeform mark can have at most ${WebMarkShape.MAX_POINTS} points"

/** Ids of [pending] marks that the stored items already show (or whose item is gone), so the layer can stop overriding them. */
internal fun settledMarks(pending: Map<Long, MarkDraft>, items: List<WebItem>, doc: DocScroll): Set<Long> =
    pending.filter { (id, mark) -> items.firstOrNull { it.id() == id }?.let { it.shape() == mark.toShape(doc.w, doc.h) } ?: true }.keys

/** The marks drawn over the page: finishing one (comment popover), moving and resizing saved ones, deleting and scrolling to them. */
internal class WebMarks(
    private val service: WebReviewService,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
    private val drafts: WebDrafts,
    private val debounceMillis: Long = SAVE_DEBOUNCE_MILLIS,
) {
    private val saves = ConcurrentHashMap<Long, Job>()

    /** A finished mark opens the comment popover at its corner; refused while another draft holds typed work. */
    fun finish(view: WebView, mark: MarkDraft) {
        val doc = view.scroll ?: return
        val shape = mark.toShape(doc.w, doc.h) ?: return reportError(TOO_MANY_POINTS)
        if (view.draftDirty) return reportError(DRAFT_FIRST_NOTICE)
        view.selectedItemId = null
        drafts.beginShape(view, shape, cornerOf(view, shape))
    }

    /** Closes the polygon being placed; false (nothing changes) when it has too few points or no area. */
    fun closePolygon(view: WebView): Boolean {
        val doc = view.scroll ?: return false
        val mark = view.polygon.close(doc.w.toFloat(), doc.h.toFloat()) ?: return false
        view.polygon.clear()
        finish(view, mark)
        return true
    }

    /**
     * Shows the new geometry at once and saves it once the pointer has been still for the debounce time, then stores a fresh screenshot
     * (the save drops the old one). Only an OPEN mark can change; an item that vanished or was sent meanwhile is dropped silently.
     */
    fun edit(view: WebView, item: WebItem, mark: MarkDraft) {
        if (item.status() != WebItemStatus.OPEN) return
        val doc = view.scroll ?: return
        val shape = mark.toShape(doc.w, doc.h) ?: return
        val id = item.id()
        view.pendingMarks[id] = mark
        saves.put(id, scope.launch {
            delay(debounceMillis)
            runGuarded({ message -> view.pendingMarks.remove(id); reportError(message) }) {
                if (io { service.tryUpdateItemShape(id, shape) }) {
                    drafts.refreshScreenshot(view, id, shape)
                    scope.launch { delay(PENDING_GRACE_MILLIS); view.pendingMarks.remove(id, mark) }
                } else view.pendingMarks.remove(id)
            }
            saves.remove(id, coroutineContext[Job])
        })?.cancel()
    }

    /** Stops a pending save of a mark that is going away, so it cannot fail or resurrect it. */
    fun discard(itemId: Long) {
        saves.remove(itemId)?.cancel()
    }

    /** Runs [action] once every pending save has landed, so a revision is never sent or approved with a stale shape. */
    fun afterSaves(action: () -> Unit) {
        val running = saves.values.toList()
        if (running.isEmpty()) action() else scope.launch { running.joinAll(); action() }
    }

    /** Deletes the selected mark; only an OPEN one that is shown (the device scope hides the others, sent ones are history). */
    fun deleteSelected(view: WebView, delete: (WebItem) -> Unit) {
        val item = view.visibleMarks.firstOrNull { it.id() == view.selectedItemId && it.status() == WebItemStatus.OPEN } ?: return
        delete(item)
        view.selectedItemId = null
        view.pendingMarks.remove(item.id())
    }

    fun scrollTo(view: WebView, shape: WebMarkShape) {
        val scroll = view.scroll ?: return
        val layout = view.layout ?: return
        val (dx, dy) = scrollDeltaTo(shape, scroll, layout.widthCss.toDouble(), layout.heightCss.toDouble())
        view.host.runScript(InspectorBridge.scrollBy(dx, dy))
    }

    private fun cornerOf(view: WebView, shape: WebMarkShape): Offset =
        view.markSpace?.viewport?.toScreen((shape.x + shape.w).toFloat(), (shape.y + shape.h).toFloat())?.let { Offset(it.first, it.second) } ?: view.layoutCentre()
}
