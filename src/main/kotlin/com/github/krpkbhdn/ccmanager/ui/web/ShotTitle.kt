package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.PromptText
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageShot
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val PERCENT = 100
private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Title and note of a screenshot handed to the Review tab; the page title is untrusted text, so it is flattened to one line and capped. */
internal object ShotTitle {
    private const val NOTE_PREFIX = "Captured by the user in the Web tab from "

    fun title(pageTitle: String, url: String, device: String, now: LocalDateTime): String {
        val suffix = " - $device - ${STAMP.format(now)}"
        val page = oneLine(pageTitle).ifEmpty { hostAndPath(url) }.take((ReviewService.MAX_CAPTURE_TITLE - suffix.length).coerceAtLeast(0)).trim()
        return page + suffix
    }

    /** The URL part is cut when the note would exceed the cap, so the scale and cut remarks always survive. */
    fun note(url: String, device: String, widthCss: Int, heightCss: Int, shot: PageShot): String {
        val tail = buildList {
            add("$device ${widthCss}x$heightCss CSS px")
            if (shot.scale < 1.0) add("scaled to ${(shot.scale * PERCENT).roundToInt()}%")
            if (shot.cut) add("cut at ${shot.cssHeight.roundToInt()} px")
        }.joinToString("; ")
        val room = (ReviewService.MAX_CAPTURE_NOTE - NOTE_PREFIX.length - ", ".length - tail.length).coerceAtLeast(0)
        return NOTE_PREFIX + oneLine(withoutQuery(url)).take(room) + ", " + tail
    }

    fun withoutQuery(url: String): String = url.substringBefore('#').substringBefore('?')

    private fun oneLine(text: String): String = PromptText.oneLine(text)

    private fun hostAndPath(url: String): String {
        val uri = runCatching { URI(withoutQuery(url)) }.getOrNull()
        val host = uri?.host ?: return oneLine(withoutQuery(url))
        return host + uri.path.orEmpty().let { if (it == "/") "" else it }
    }
}
