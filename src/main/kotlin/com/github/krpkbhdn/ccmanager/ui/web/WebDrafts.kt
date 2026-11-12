package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebReviewService
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
                        val item = io { service.addItem(view.projectId, draft.pageUrl, draft.pageTitle, draft.target.toAnchor(), draft.comment, changes, viewport) }
                        view.selectedItemId = item.id()
                        attachScreenshot(item, draft.shot)
                    }
                }
            } finally {
                view.draftSaving = false
            }
        }
    }

    private fun putBack(view: WebView, draft: DraftSnapshot) {
        if (view.picked != null) return
        view.restoreDraft(draft)
        val changes = view.styleChanges
        if (changes.isNotEmpty()) view.host.runScript(InspectorBridge.previewStyle(draft.target.selector, changes.associate { it.property to it.to }))
    }

    /** Taken when the element is picked, so it matches what the user saw even if the page scrolls before saving. */
    fun captureAtPick(view: WebView, target: ElementTarget) {
        view.pickViewport = view.currentViewport()
        val area = target.viewportRect ?: return
        val generation = view.draftGeneration
        scope.launch {
            val png = try {
                io { view.host.capture(area.x.roundToInt(), area.y.roundToInt(), max(1, ceil(area.w).toInt()), max(1, ceil(area.h).toInt())) }
            } catch (e: RuntimeException) {
                log.warn("Element screenshot failed: {}", e.javaClass.simpleName)
                null
            }
            if (view.draftGeneration == generation) view.pickShot = png
        }
    }

    private suspend fun attachScreenshot(item: WebItem, png: ByteArray?) {
        if (png == null) return
        try {
            io { service.saveScreenshot(item.id(), png) }
        } catch (e: RuntimeException) {
            log.warn("Saving the element screenshot failed: {}", e.message)
        }
    }
}
