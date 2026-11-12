package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the context menu was opened: the page point, the canvas offset, the layers under it, the layer the actions apply to and whether the feedback popover replaced the menu. */
data class ContextMenuState(
    val pageX: Double,
    val pageY: Double,
    val canvasOffset: Offset,
    val items: List<LiteTarget>,
    val chosen: Int = 0,
    val feedback: Boolean = false,
    val notice: String? = null,
    val quick: Boolean = false,
)

internal const val REASON_FEEDBACK = "feedback"
internal const val REASON_STYLES = "styles"
internal const val DRAFT_FIRST_NOTICE = "Finish or cancel the current draft first"
internal const val ELEMENT_GONE_NOTICE = "The element is gone"
internal const val NOTHING_HERE_NOTICE = "Nothing here"
internal const val NO_POINTER_NOTICE = "Move the pointer over the page first"

// How long an inspect answer is awaited; the page may have changed or dropped the message, so the menu must not wait forever.
internal const val INSPECT_TIMEOUT_MILLIS = 5_000L

private class PendingInspect(val selector: String, val reason: String)

/** Reacts to the page's Stack and Inspect messages: opens the layer menu and feedback popover. [onDraftStarted] takes the element screenshot; [notify] shows a short notice. */
internal class WebContextActions(
    private val scope: CoroutineScope,
    private val inspectTimeoutMillis: Long = INSPECT_TIMEOUT_MILLIS,
    private val notify: (String) -> Unit = {},
    private val onDraftStarted: (WebView, ElementTarget) -> Unit = { _, _ -> },
) {
    /** Quick feedback (Q): feedback on the top layer under the last pointer position, through the same stack and pending-inspect flow as the menu, without showing a menu. */
    fun openQuick(view: WebView) {
        val spot = view.lastPointer ?: return notify(NO_POINTER_NOTICE)
        if (view.draftDirty) return notify(DRAFT_FIRST_NOTICE)
        pending.remove(view)
        clearSimilar(view)
        view.contextMenu = ContextMenuState(spot.page.x.toDouble(), spot.page.y.toDouble(), spot.canvas, emptyList(), quick = true)
        view.host.runScript(InspectorBridge.stackAt(spot.page.x.toDouble(), spot.page.y.toDouble()))
    }

    /** Starts a draft on [target] and shows the feedback popover at [canvasOffset]. */
    fun openFeedbackAt(view: WebView, target: ElementTarget, canvasOffset: Offset) {
        view.beginDraft(target)
        onDraftStarted(view, target)
        view.contextMenu = ContextMenuState(0.0, 0.0, canvasOffset, emptyList(), feedback = true)
    }

    private fun refuse(view: WebView, notice: String) {
        val menu = view.contextMenu
        if (menu?.quick == true) {
            dismiss(view)
            notify(notice)
        } else if (menu != null) view.contextMenu = menu.copy(notice = notice)
    }

    fun open(view: WebView, pageX: Double, pageY: Double, canvasOffset: Offset) {
        pending.remove(view)
        clearSimilar(view)
        view.contextMenu = ContextMenuState(pageX, pageY, canvasOffset, emptyList())
        view.host.runScript(InspectorBridge.stackAt(pageX, pageY))
    }

    fun onStack(view: WebView, message: InspectorMessage.Stack) {
        val menu = view.contextMenu ?: return
        if (menu.pageX != message.x || menu.pageY != message.y) return
        view.contextMenu = menu.copy(items = message.items)
        if (!menu.quick) return
        message.items.firstOrNull()?.let { requestInspect(view, it.selector, REASON_FEEDBACK) } ?: refuse(view, NOTHING_HERE_NOTICE)
    }

    /** An inspect answer is taken only for the request Kotlin issued; [reset] drops it when the page changes. */
    fun onInspect(view: WebView, message: InspectorMessage.Inspect) {
        val request = pending.remove(view) ?: return
        if (message.reason != request.reason || message.target.selector != request.selector) return
        val menu = view.contextMenu
        if (view.draftDirty) return refuse(view, DRAFT_FIRST_NOTICE)
        view.beginDraft(message.target)
        onDraftStarted(view, message.target)
        if (message.reason == REASON_FEEDBACK && menu != null) view.contextMenu = menu.copy(feedback = true) else dismiss(view)
    }

    fun reset(view: WebView) {
        pending.remove(view)
        clearSimilar(view)
        view.contextMenu = null
    }

    /** Asks the page to describe [selector]; keeps the menu with a notice instead of asking while a draft is open. */
    fun requestInspect(view: WebView, selector: String, reason: String) {
        if (view.draftDirty) return refuse(view, DRAFT_FIRST_NOTICE)
        val request = PendingInspect(selector, reason)
        pending[view] = request
        view.host.runScript(InspectorBridge.describeSelector(selector, reason))
        scope.launch {
            delay(inspectTimeoutMillis)
            if (pending.remove(view, request)) refuse(view, ELEMENT_GONE_NOTICE)
        }
    }

    companion object {
        // Shared so the static dismiss can drop a late answer; the entry is identity-checked by the timeout.
        private val pending = ConcurrentHashMap<WebView, PendingInspect>()

        fun dismiss(view: WebView) {
            pending.remove(view)
            view.contextMenu = null
            view.host.runScript(InspectorBridge.highlight(""))
            clearSimilar(view)
        }

        /** The page clears the similar outline when asked to show it for an empty selector. */
        fun clearSimilar(view: WebView) = view.host.runScript(InspectorBridge.showSimilar(""))
    }
}
