package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.runtime.mutableStateMapOf
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewService.CaptureTarget
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

/**
 * The reviews a web screenshot can be added to and the one each project's browser is pinned to (not persisted). Lives in [WebState]
 * because the browser pool evicts views.
 */
internal class WebCaptureTargets(
    private val reviews: ReviewService,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    /** project id to the review the next screenshot is added to. */
    val pinned = mutableStateMapOf<Long, Long>()
    private val byProject = mutableStateMapOf<Long, List<CaptureTarget>>()
    private val watched = ConcurrentHashMap.newKeySet<Long>()
    private val subscription = lazy { reviews.subscribe(Runnable { reload.request() }) }
    private val reload = Refresher(scope) {
        watched.toList().forEach { projectId ->
            runGuarded(reportError) { byProject[projectId] = io { reviews.captureTargets(projectId) } }
        }
    }

    /** Reviews that still take screenshots, newest first; observable. */
    fun targets(projectId: Long): List<CaptureTarget> {
        subscription.value
        if (watched.add(projectId)) reload.request()
        return byProject[projectId].orEmpty()
    }

    fun pin(projectId: Long, reviewId: Long) {
        pinned[projectId] = reviewId
        reload.request()
    }

    /** Pins a review just created: the list is read first (blocking, call off the UI thread) so the pin is never judged stale before it knows the review. */
    fun pinCreated(projectId: Long, reviewId: Long) {
        byProject[projectId] = reviews.captureTargets(projectId)
        pinned[projectId] = reviewId
    }

    fun unpin(projectId: Long) {
        pinned.remove(projectId)
    }

    /** The pin points at a review that was sent, approved, deleted or is full. */
    fun stale(projectId: Long): Boolean {
        val id = pinned[projectId] ?: return false
        return targets(projectId).firstOrNull { it.id() == id }?.full() != false
    }

    fun close() {
        if (subscription.isInitialized()) subscription.value.run()
        reload.close()
    }
}
