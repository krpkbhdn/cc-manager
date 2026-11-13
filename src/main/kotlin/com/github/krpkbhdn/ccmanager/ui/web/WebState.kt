package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.NavigationPolicy
import com.github.krpkbhdn.ccmanager.ui.web.browser.ShotArea
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebPage
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebRevision
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class ProjectData(val revisions: List<WebRevision>, val pages: List<WebPage>, val items: Map<Long, List<WebItem>>)

private const val OPEN_FAILED = "The browser could not open a page"
private const val SWEEP_INTERVAL_MILLIS = 30_000L
private const val PERCENT_100 = 100
private const val ONLY_WEB_MESSAGE = "Only http, https and file addresses can be opened"

/**
 * Web review state: one browser per project (created on first use, kept until [close]), the inspector draft, the numbered items of every
 * revision and sending a revision to an agent. Service calls run off the UI thread.
 */
class WebState(
    private val service: WebReviewService,
    private val hostFactory: () -> WebPageHost,
    private val enqueue: SessionEnqueue,
    private val startSession: SessionStarter,
    private val live: () -> LiveSessions,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
    reviewService: ReviewService,
    onCaptured: (projectId: Long, reviewId: Long) -> Unit,
    confirm: (ConfirmRequest) -> Unit,
    private val availability: () -> Availability = { Availability.Ready },
    private val prepareBrowser: (() -> Unit)? = null,
    clock: Clock = Clock.systemUTC(),
) {
    private val drafts = WebDrafts(service, scope, reportError)
    private val context = WebContextActions(scope, notify = reportError) { view, target -> drafts.captureAtPick(view, target) }
    private val events = WebPageEvents(service, drafts, scope, reportError, context)
    private val delivery = WebDelivery(service, enqueue, startSession, live, scope, reportError)
    private val targets = WebCaptureTargets(reviewService, scope, reportError)
    private val screenshots = WebScreenshots(reviewService, targets, onCaptured, confirm, scope, reportError, clock)

    /** False until [prepare] has checked the runtime and started the engine: the engine reports its start on the UI thread, so it must not be awaited from there. */
    var browserReady by mutableStateOf(false)
        private set
    private var unsupported by mutableStateOf<String?>(null)
    private val preparing = AtomicBoolean()

    private val pool = WebViewPool(hostFactory, service::browserLimits, clock, ::wire)
    private var sweeper: Job? = null
    private val revisionsByProject = mutableStateMapOf<Long, List<WebRevision>>()
    private val pagesByProject = mutableStateMapOf<Long, List<WebPage>>()
    private val itemsByRevision = mutableStateMapOf<Long, List<WebItem>>()
    private val watchedProjects = ConcurrentHashMap.newKeySet<Long>()
    private val subscription = lazy { service.subscribe(Runnable { reload.request() }) }
    private val reload = Refresher(scope) {
        watchedProjects.toList().forEach { projectId ->
            runGuarded(reportError) {
                val data = io { load(projectId) }
                if (projectId !in watchedProjects) return@runGuarded
                (revisionsByProject[projectId].orEmpty().map { it.id() } - data.items.keys).forEach(itemsByRevision::remove)
                revisionsByProject[projectId] = data.revisions
                pagesByProject[projectId] = data.pages
                data.items.forEach { (revisionId, items) -> itemsByRevision[revisionId] = items }
                pool.find(projectId)?.let { events.refreshPage(it) }
            }
        }
    }

    /** Why the browser cannot run here, or null when it can (or [prepare] has not answered yet). */
    fun unsupportedReason(): String? = unsupported

    /** Why the engine could not start, null while it is starting or running. */
    var browserFailure by mutableStateOf<String?>(null)
        private set

    /** Checks the runtime and starts the browser engine off the UI thread, once; [browserReady] flips when it can host pages, [browserFailure] says why not. */
    fun prepare() {
        if (!preparing.compareAndSet(false, true)) return
        scope.launch {
            runGuarded({ browserFailure = it }) {
                unsupported = (io(availability) as? Availability.Unsupported)?.reason
                if (unsupported == null) {
                    prepareBrowser?.let { start -> io { start() } }
                    browserReady = true
                }
            }
        }
    }

    /** Clears a start failure and tries to start the engine again. */
    fun retry() {
        browserFailure = null
        preparing.set(false)
        prepare()
    }

    /** Disposes every browser; called once when the app exits. */
    fun close() {
        if (subscription.isInitialized()) subscription.value.run()
        reload.close()
        targets.close()
        sweeper?.cancel()
        pool.closeAll()
    }

    /** Closes the browser of a project that no longer exists. */
    fun forget(projectId: Long) {
        watchedProjects.remove(projectId)
        revisionsByProject.remove(projectId)?.forEach { itemsByRevision.remove(it.id()) }
        pagesByProject.remove(projectId)
        pool.close(projectId)
    }

    /** Closes the browsers and drops the cached revisions of every project that is not in [existing]. */
    fun retain(existing: Collection<Long>) {
        (watchedProjects + revisionsByProject.keys - existing.toSet()).forEach(::forget)
        pool.retain(existing)
    }

    /** The browser of the project if one is open; observable, so a screen recomposes when it opens or closes. */
    fun liveView(projectId: Long): WebView? = pool.find(projectId)

    fun shown(projectId: Long) = pool.shown(projectId)

    fun hidden(projectId: Long) = pool.hidden(projectId)

    internal fun sweepIdle() = pool.closeIdle()

    private fun load(projectId: Long): ProjectData {
        val revisions = service.revisions(projectId)
        return ProjectData(revisions, service.pages(projectId), revisions.associate { it.id() to service.allItems(it.id()) })
    }

    private fun watch(projectId: Long) {
        subscription.value
        if (watchedProjects.add(projectId)) reload.request()
    }

    fun revisions(projectId: Long): List<WebRevision> {
        watch(projectId)
        return revisionsByProject[projectId].orEmpty()
    }

    fun pages(projectId: Long): List<WebPage> = pagesByProject[projectId].orEmpty()

    fun items(revisionId: Long): List<WebItem> = itemsByRevision[revisionId].orEmpty()

    /** OPEN items of the project's PENDING revision, for the tab badge. */
    fun pendingCount(projectId: Long): Int {
        val pending = revisions(projectId).firstOrNull { it.status() == ReviewStatus.PENDING } ?: return 0
        return items(pending.id()).count { it.status() == WebItemStatus.OPEN }
    }

    fun view(projectId: Long): WebView {
        watch(projectId)
        return pool.open(projectId)
    }

    /** Opens the project's browser from an effect; a native failure is shown through [browserFailure] and never reaches composition. */
    fun open(projectId: Long): WebView? = try {
        view(projectId)
    } catch (e: Exception) {
        browserFailure = e.message ?: OPEN_FAILED
        null
    } catch (e: LinkageError) {
        browserFailure = e.message ?: OPEN_FAILED
        null
    }

    private fun wire(view: WebView) {
        view.jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) { view.host.events.collect { events.onEvent(view, it) } }
        view.jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            view.host.page.map { it.url }.distinctUntilChanged().collect { if (it.isNotEmpty()) view.address.setTextAndPlaceCursorAtEnd(it) }
        }
        view.jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { view.device.scope }.distinctUntilChanged().drop(1).collect { view.itemPopover = null; events.pushPins(view) }
        }
        view.jobs += scope.launch { runGuarded(reportError) { events.restoreLastPage(view) } }
        if (sweeper == null) sweeper = scope.launch { while (true) { delay(SWEEP_INTERVAL_MILLIS); pool.closeIdle() } }
    }

    fun go(view: WebView, typed: String) {
        val url = normalizeUrl(typed)
        when {
            url.isEmpty() -> Unit
            !NavigationPolicy.isAllowed(url) -> reportError(ONLY_WEB_MESSAGE)
            else -> try {
                view.host.load(url)
            } catch (e: RuntimeException) {
                reportError(e.message ?: ONLY_WEB_MESSAGE)
            }
        }
    }

    fun setToggles(view: WebView, toggles: InspectorToggles) = events.setToggles(view, toggles)


    /** Carries out a hotkey or toolbar action; [zoomStep] (notches, positive in) is the screen's, which owns the pan. */
    fun perform(view: WebView, action: WebAction, zoomStep: (Int) -> Unit = {}) {
        val toggles = view.toggles
        when (action) {
            WebAction.TogglePick -> togglePick(view)
            WebAction.StickyPick -> stickyPick(view)
            WebAction.QuickFeedback -> context.openQuick(view)
            WebAction.ToggleOverlays -> setToggles(view, toggles.toggleMaster())
            is WebAction.ToggleGroup -> setToggles(view, toggles.toggleGroup(action.group))
            WebAction.ToggleLock -> view.interaction = !view.interaction
            WebAction.Rotate -> if (!view.device.responsive) view.device = view.device.copy(rotated = !view.device.rotated)
            WebAction.ZoomFit -> if (!view.device.responsive) view.device = view.device.copy(zoom = DeviceZoom.Fit)
            WebAction.Zoom100 -> if (!view.device.responsive) view.device = view.device.copy(zoom = DeviceZoom.Percent(PERCENT_100))
            WebAction.ZoomIn -> zoomStep(1)
            WebAction.ZoomOut -> zoomStep(-1)
            WebAction.CloseMenu -> closeMenu(view)
            WebAction.StopPick -> stopPicking(view)
            is WebAction.Screenshot -> screenshots.capture(view, action.area)
            WebAction.StartRegion -> startRegion(view)
            WebAction.CancelRegion -> view.regionSelecting = false
        }
    }

    fun canShoot(view: WebView) = screenshots.canShoot(view)

    /** Reviews of the project a screenshot can join (pending, observable). */
    fun captureTargets(projectId: Long) = targets.targets(projectId)

    /** The review the next screenshot of the project joins, if one is pinned. */
    fun pinnedReview(projectId: Long): Long? = targets.pinned[projectId]

    fun pin(projectId: Long, reviewId: Long) = targets.pin(projectId, reviewId)

    /** The next screenshot creates a new review. */
    fun unpin(projectId: Long) = targets.unpin(projectId)

    fun pinIsStale(projectId: Long) = targets.stale(projectId)

    /** The region overlay replaces pick mode and any open menu, so the drag cannot hit either. */
    private fun startRegion(view: WebView) {
        if (!screenshots.canStart(view)) return
        if (view.toggles.pick) stopPicking(view)
        if (view.contextMenu != null) closeMenu(view)
        view.regionScroll = null
        view.regionSelecting = true
        scope.launch { runGuarded(reportError) { view.regionScroll = io { view.host.scrollOffset() } } }
    }

    /** The region is relative to the viewport as it was when selected, so the scroll read at that time goes with it. */
    fun regionSelected(view: WebView, rect: CssRect) {
        view.regionSelecting = false
        screenshots.capture(view, ShotArea.Region(rect.x, rect.y, rect.width, rect.height, view.regionScroll))
    }

    private fun togglePick(view: WebView) {
        if (view.toggles.pick) stopPicking(view) else startPicking(view, sticky = false)
    }

    private fun stickyPick(view: WebView) {
        if (view.stickyPick) stopPicking(view) else startPicking(view, sticky = true)
    }

    private fun startPicking(view: WebView, sticky: Boolean) {
        setToggles(view, view.toggles.copy(pick = true))
        view.stickyPick = sticky && view.toggles.pick
    }

    private fun stopPicking(view: WebView) {
        setToggles(view, view.toggles.copy(pick = false))
    }

    /** Esc on a menu or popover: drops a late inspect answer with it, and a feedback popover takes its draft along, as its own Esc does. */
    private fun closeMenu(view: WebView) {
        val feedback = view.contextMenu?.feedback == true
        WebContextActions.dismiss(view)
        if (feedback) cancelDraft(view)
    }

    /** Right-click in the page: opens the layer menu at [canvasOffset] (px) for the page point. */
    fun openContextMenu(view: WebView, pagePoint: Offset, canvasOffset: Offset) = context.open(view, pagePoint.x.toDouble(), pagePoint.y.toDouble(), canvasOffset)

    fun requestInspect(view: WebView, selector: String, reason: String) = context.requestInspect(view, selector, reason)

    fun deleteOnKey(view: WebView, item: WebItem, key: Key, modified: Boolean): Boolean = ItemKeys.onKey(view, item, key, modified, ::delete)

    fun editStyle(view: WebView, property: String, value: String) = drafts.editStyle(view, property, value)

    fun cancelDraft(view: WebView) = drafts.cancel(view)

    fun editItem(view: WebView, item: WebItem) = drafts.editItem(view, item)

    fun saveDraft(view: WebView) = drafts.save(view)

    /** Selects the item and shows its pin, first opening its page when the browser is elsewhere. */
    fun selectItem(view: WebView, item: WebItem) {
        view.selectedItemId = item.id()
        val page = pages(view.projectId).firstOrNull { it.id() == item.pageId() }
        if (page == null || page.pageKey() == view.pageKey) {
            view.host.runScript(InspectorBridge.focusAnnotation(item.id().toString()))
        } else {
            view.pendingFocus = item.id()
            view.host.load(page.lastUrl())
        }
    }

    fun setStatus(item: WebItem, status: WebItemStatus) = guarded { service.setStatus(item.id(), status) }

    fun delete(item: WebItem) = guarded { service.deleteItem(item.id()) }

    fun approve(revision: WebRevision) = guarded { service.approve(revision.id()) }

    fun startNextRevision(projectId: Long) = guarded { service.startNextRevision(projectId) }

    private fun guarded(work: () -> Unit) {
        scope.launch { runGuarded(reportError) { io(work) } }
    }

    fun send(revision: WebRevision) = delivery.send(revision)

    fun sendTo(revision: WebRevision, ccmSessionId: String?) = delivery.sendTo(revision, ccmSessionId)

    fun sendTarget(projectId: Long): WebSendTarget? = delivery.sendTarget(projectId)

    fun dismissSendTarget(projectId: Long) = delivery.dismissSendTarget(projectId)

    fun isSending(revisionId: Long): Boolean = delivery.isSending(revisionId)
}
