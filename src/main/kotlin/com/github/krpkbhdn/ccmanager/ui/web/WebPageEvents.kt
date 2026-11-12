package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger(WebPageEvents::class.java)
private const val BLANK_PAGE = "about:blank"
private const val RESTORE_RETRY_MILLIS = 3_000L
private const val DRAFT_OPEN_MESSAGE = "Finish or cancel the current item before picking another element"

/** What the page tells us: console lines, inspector messages and page changes, and the pins and options pushed back into it. */
internal class WebPageEvents(
    private val service: WebReviewService,
    private val drafts: WebDrafts,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
    private val context: WebContextActions,
    private val pinActions: WebPinActions = WebPinActions(),
) {
    suspend fun onEvent(view: WebView, event: PageEvent) {
        when (event) {
            is PageEvent.Console -> view.addConsole(ConsoleLine(event.level, event.text, event.source, event.line, System.currentTimeMillis()))
            is PageEvent.LoadFailed -> view.addConsole(ConsoleLine(ConsoleLevel.ERROR, "Could not load ${event.url}: ${event.error}", event.url, 0, System.currentTimeMillis()))
            is PageEvent.Inspector -> withContext(Dispatchers.Default) { InspectorMessage.parse(event.json) }?.let { onInspector(view, it) }
        }
    }

    suspend fun restoreLastPage(view: WebView) {
        val last = io { service.pages(view.projectId) }.lastOrNull()?.lastUrl() ?: return
        if (!isBlankPage(view.host.page.value.url)) return
        view.host.load(last)
        // a load issued right after the browser opened can be dropped; one retry if no page shows up
        if (withTimeoutOrNull(RESTORE_RETRY_MILLIS) { view.host.page.first { !isBlankPage(it.url) } } == null) view.host.load(last)
    }

    private fun isBlankPage(url: String) = url.isEmpty() || url == BLANK_PAGE

    /** Loads the page's items and pushes pins and options into it; also resolves a pending jump to an item. */
    suspend fun refreshPage(view: WebView) {
        val key = view.pageKey
        val items = if (key == null) emptyList() else io { service.items(view.projectId, key) }
        if (pageKeyOf(view.host.page.value.url) != key) return
        view.pageItems = items
        view.host.runScript(InspectorBridge.setOptions(view.toggles.effective()))
        pushPins(view)
        view.pendingFocus?.let { id ->
            view.pendingFocus = null
            if (view.visibleItems.any { it.id() == id }) view.host.runScript(InspectorBridge.focusAnnotation(id.toString()))
        }
    }

    /** Sends the pins of the items visible for the current device scope; each keeps its own number. */
    fun pushPins(view: WebView) = view.host.runScript(InspectorBridge.setAnnotations(view.visibleItems.map(WebItem::toPin)))

    /** Pick mode is the page's permission to send elements; it is refused while typed work is open, so nothing the user wrote is replaced. */
    fun setToggles(view: WebView, toggles: InspectorToggles) {
        if (toggles.pick && view.draftDirty) {
            reportError(DRAFT_OPEN_MESSAGE)
            return
        }
        if (!toggles.pick) view.stickyPick = false
        view.toggles = toggles
        view.host.runScript(InspectorBridge.setOptions(toggles.effective()))
    }

    private suspend fun onInspector(view: WebView, message: InspectorMessage) {
        when (message) {
            is InspectorMessage.Ready -> pageChanged(view, message.title)
            is InspectorMessage.Navigated -> pageChanged(view, message.title)
            is InspectorMessage.Hover -> if (view.toggles.pick) view.hovered = message.target
            is InspectorMessage.Pick -> if (view.toggles.pick) onPick(view, message.target)
            InspectorMessage.PickCancel -> setToggles(view, view.toggles.copy(pick = false))
            is InspectorMessage.Stack -> context.onStack(view, message)
            is InspectorMessage.Inspect -> context.onInspect(view, message)
            is InspectorMessage.ModuleError -> LOG.debug("Inspector module {} reported an error", message.module)
            is InspectorMessage.AnnotationHover -> pinActions.onHover(view, message)
            InspectorMessage.AnnotationLeave -> pinActions.onLeave(view)
            is InspectorMessage.Editing -> view.pageEditing = message.on
            is InspectorMessage.AnnotationClick -> view.visibleItems.firstOrNull { it.id().toString() == message.id }?.let {
                view.selectedItemId = it.id()
                view.panelTab = WebPanelTab.ITEMS
                pinActions.emphasiseSelection(view)
            }
        }
    }

    /** A pick ends pick mode, except sticky pick: it stays on and opens the feedback popover at the pointer. */
    private fun onPick(view: WebView, target: ElementTarget) {
        if (view.draftDirty) {
            if (view.stickyPick) reportError(DRAFT_OPEN_MESSAGE)
            return
        }
        WebContextActions.clearSimilar(view)
        if (view.stickyPick) {
            context.openFeedbackAt(view, target, view.lastPointer?.canvas ?: view.layoutCentre())
            view.host.runScript(InspectorBridge.setOptions(view.toggles.effective()))
        } else {
            view.beginDraft(target)
            setToggles(view, view.toggles.copy(pick = false))
            drafts.captureAtPick(view, target)
        }
    }

    /** The page address is the browser's own, never the one the page claims in its message, so a hostile page cannot ask for another page's items. */
    private suspend fun pageChanged(view: WebView, title: String) {
        val url = view.host.page.value.url
        val key = pageKeyOf(url)
        if (key != view.pageKey) {
            if (!view.draftDirty) view.clearDraft()
            view.selectedItemId = null
        }
        context.reset(view)
        view.itemPopover = null
        if (key != null && url != view.pageUrl) scope.launch { runGuarded(reportError) { io { service.touchPage(view.projectId, url, title) } } }
        view.pageUrl = url
        view.pageTitle = title
        view.pageKey = key
        view.hovered = null
        view.pageEditing = false
        runGuarded(reportError) { refreshPage(view) }
    }
}
