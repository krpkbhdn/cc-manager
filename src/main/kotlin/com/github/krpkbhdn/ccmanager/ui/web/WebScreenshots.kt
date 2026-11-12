package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.ui.web.browser.ShotArea
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val NO_SHOT = "The page could not be photographed (it may have changed during the capture)"
private const val STILL_LOADING = "The page is still loading: take the screenshot when it has finished."
private const val NEW_REVIEW = "New review"

private class Shot(val title: String, val note: String, val bytes: ByteArray, val label: String)

/**
 * Photographs the page for the Review tab: one capture at a time per browser, off the UI thread. The first one creates a review that
 * is pinned; later ones join it until it is full, settled or gone, when the user is asked whether to start a new one.
 */
internal class WebScreenshots(
    private val reviews: ReviewService,
    private val targets: WebCaptureTargets,
    private val onCaptured: (projectId: Long, reviewId: Long) -> Unit,
    private val confirm: (ConfirmRequest) -> Unit,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
    private val clock: Clock,
) {
    /** A blank tab has nothing to photograph, and a capture already running must finish first. */
    fun canShoot(view: WebView): Boolean = !view.shooting && view.host.page.value.url.let { it.isNotBlank() && !it.startsWith("about:") }

    /** [canShoot], and a page that is still loading is refused with a notice, since its layout is not final. */
    fun canStart(view: WebView): Boolean {
        if (!canShoot(view)) return false
        if (view.host.page.value.loading) reportError(STILL_LOADING)
        return !view.host.page.value.loading
    }

    fun capture(view: WebView, area: ShotArea) {
        if (!canStart(view)) return
        view.shooting = true
        val page = view.host.page.value
        val device = view.device.presetLabel()
        val size = view.layout
        scope.launch {
            try {
                runGuarded(reportError) {
                    io {
                        val shot = shoot(view, area, page.url, page.title, device, size?.widthCss ?: 0, size?.heightCss ?: 0)
                        place(view.projectId, shot)
                    }?.let { onCaptured(view.projectId, it) }
                }
            } finally {
                view.shooting = false
            }
        }
    }

    private fun shoot(view: WebView, area: ShotArea, url: String, title: String, device: String, widthCss: Int, heightCss: Int): Shot {
        val shot = view.host.screenshot(area, reviews.limits()) ?: throw IllegalStateException(NO_SHOT)
        return Shot(
            ShotTitle.title(title, url, device, LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))),
            ShotTitle.note(url, device, widthCss, heightCss, shot), shot.bytes, "${areaLabel(area)} - $url",
        )
    }

    /** The review the shot went into, or null when it waits for the user's answer. */
    private fun place(projectId: Long, shot: Shot): Long? {
        val pinned = targets.pinned[projectId] ?: return create(projectId, shot)
        return try {
            reviews.addCapture(pinned, projectId, shot.bytes, shot.label)
            pinned
        } catch (e: ReviewService.CaptureRefusedException) {
            targets.unpin(projectId)
            askNewReview(projectId, shot, e)
            null
        }
    }

    private fun create(projectId: Long, shot: Shot): Long =
        reviews.submitCapture(projectId, shot.title, shot.note, shot.bytes, shot.label).id().also { targets.pinCreated(projectId, it) }

    private fun askNewReview(projectId: Long, shot: Shot, refusal: ReviewService.CaptureRefusedException) {
        val title = if (refusal.reason() == ReviewService.Reason.FULL) "Review is full" else "Review was sent, approved or deleted"
        confirm(
            ConfirmRequest(title, "${refusal.message} Create a new review for this screenshot?", NEW_REVIEW, destructive = false) {
                scope.launch { runGuarded(reportError) { onCaptured(projectId, io { create(projectId, shot) }) } }
            },
        )
    }

    private fun areaLabel(area: ShotArea) = when (area) {
        ShotArea.Visible -> "Visible part"
        ShotArea.WholePage -> "Whole page"
        is ShotArea.Region -> "Region"
    }
}
