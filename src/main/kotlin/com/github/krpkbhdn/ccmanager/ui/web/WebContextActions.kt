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
)

internal const val REASON_FEEDBACK = "feedback"
internal const val REASON_STYLES = "styles"
internal const val DRAFT_FIRST_NOTICE = "Finish or cancel the current draft first"
internal const val ELEMENT_GONE_NOTICE = "The element is gone"

// How long an inspect answer is awaited; the page may have changed or dropped the message, so the menu must not wait forever.
internal const val INSPECT_TIMEOUT_MILLIS = 5_000L

private class PendingInspect(val selector: String, val reason: String)

/** Reacts to the page's Stack and Inspect messages: opens the layer menu and feedback popover. [onDraftStarted] takes the element screenshot. */
internal class WebContextActions(
    private val scope: CoroutineScope,
    private val inspectTimeoutMillis: Long = INSPECT_TIMEOUT_MILLIS,
    private val onDraftStarted: (WebView, ElementTarget) -> Unit = { _, _ -> },
) {
    fun open(view: WebView, pageX: Double, pageY: Double, canvasOffset: Offset) {
        pending.remove(view)
        clearSimilar(view)
        view.contextMenu = ContextMenuState(pageX, pageY, canvasOffset, emptyList())
        view.host.runScript(InspectorBridge.stackAt(pageX, pageY))
    }

    fun onStack(view: WebView, message: InspectorMessage.Stack) {
        val menu = view.contextMenu ?: return
        if (menu.pageX == message.x && menu.pageY == message.y) view.contextMenu = menu.copy(items = message.items)
    }

    /** An inspect answer is taken only for the request Kotlin issued; [reset] drops it when the page changes. */
    fun onInspect(view: WebView, message: InspectorMessage.Inspect) {
        val request = pending.remove(view) ?: return
        if (message.reason != request.reason || message.target.selector != request.selector) return
        val menu = view.contextMenu
        if (view.draftDirty) {
            if (menu != null) view.contextMenu = menu.copy(notice = DRAFT_FIRST_NOTICE)
            return
        }
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
        if (view.draftDirty) {
            view.contextMenu = view.contextMenu?.copy(notice = DRAFT_FIRST_NOTICE)
            return
        }
        val request = PendingInspect(selector, reason)
        pending[view] = request
        view.host.runScript(InspectorBridge.describeSelector(selector, reason))
        scope.launch {
            delay(inspectTimeoutMillis)
            if (pending.remove(view, request)) view.contextMenu = view.contextMenu?.copy(notice = ELEMENT_GONE_NOTICE)
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
