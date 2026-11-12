package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.ReviewService
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

/** Photographs the page for the Review tab: one capture at a time per browser, off the UI thread, then the new review is opened. */
internal class WebScreenshots(
    private val reviews: ReviewService,
    private val openReview: (projectId: Long, reviewId: Long) -> Unit,
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
                    val review = io { shoot(view, area, page.url, page.title, device, size?.widthCss ?: 0, size?.heightCss ?: 0) }
                    openReview(view.projectId, review.id())
                }
            } finally {
                view.shooting = false
            }
        }
    }

    private fun shoot(view: WebView, area: ShotArea, url: String, title: String, device: String, widthCss: Int, heightCss: Int) =
        reviews.limits().let { limits ->
            val shot = view.host.screenshot(area, limits) ?: throw IllegalStateException(NO_SHOT)
            reviews.submitCapture(
                view.projectId, ShotTitle.title(title, url, device, LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))),
                ShotTitle.note(url, device, widthCss, heightCss, shot), shot.bytes, areaLabel(area),
            )
        }

    private fun areaLabel(area: ShotArea) = when (area) {
        ShotArea.Visible -> "Visible part"
        ShotArea.WholePage -> "Whole page"
        is ShotArea.Region -> "Region"
    }
}
