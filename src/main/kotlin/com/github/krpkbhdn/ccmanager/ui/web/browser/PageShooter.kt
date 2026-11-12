package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Screenshots through the protocol, so the inspector overlay (hidden for the shot) is not in the image. Blocking: call from IO only.
 * Page scroll and overlay visibility are restored before the image is encoded, also when the capture fails or times out; a capture
 * is dropped when the page navigates meanwhile ([page] is compared with its state at the start).
 * A whole page is stitched from viewport shots in rows and columns: windowless CEF tiles the visible surface for captureBeyondViewport and
 * crashes on fromSurface=false (checked), so fixed elements repeat in every slice (shortcut: whole-page shots of such pages are approximate).
 */
class PageShooter(private val devTools: DevTools, private val timeout: Duration, private val page: () -> PageState) {
    private val json = JsonMapper()

    private class Taken(val image: Image, val plan: ShotPlan, val metrics: PageMetrics)

    fun screenshot(area: ShotArea, limits: ShotLimits): PageShot? {
        val deadline = System.nanoTime() + timeout.toNanos()
        val taken = (
            try {
                takeAndRestore(area, limits, deadline)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Screenshot interrupted")
                null
            } catch (e: Exception) {
                log.warn("Screenshot failed: {}", e.javaClass.simpleName)
                null
            }
            ) ?: return null
        return try {
            taken.image.use { encode(it, taken.plan, taken.metrics, limits) }
        } catch (e: Exception) {
            log.warn("Screenshot encoding failed: {}", e.javaClass.simpleName)
            null
        }
    }

    /** The scroll offset of the page, or null when it cannot be read. */
    fun scrollOffset(): PageScroll? = try {
        evaluate("[scrollX, scrollY]", System.nanoTime() + timeout.toNanos()).let { PageScroll(it.path(0).asDouble(), it.path(1).asDouble()) }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: Exception) {
        null
    }

    private fun takeAndRestore(area: ShotArea, limits: ShotLimits, deadline: Long): Taken {
        val started = page()
        check(!started.loading) { "page is loading" }
        try {
            val metrics = prepare(area == ShotArea.WholePage, deadline)
            val plan = ShotPlan.of(area, metrics, limits)
            val image = if (plan.beyondViewport) stitch(plan, metrics, started, deadline) else Image.makeFromEncoded(viewportPng(plan, deadline))
            try {
                ensureSamePage(started)
            } catch (e: IllegalStateException) {
                image.close()
                throw e
            }
            return Taken(image, plan, metrics)
        } finally {
            restore()
        }
    }

    private fun ensureSamePage(started: PageState) {
        val now = page()
        check(now.url == started.url && !now.loading) { "page changed during the capture" }
    }

    private fun prepare(whole: Boolean, deadline: Long): PageMetrics {
        val value = evaluate(prepareScript(whole), deadline)
        return PageMetrics(
            value.path("sx").asDouble(), value.path("sy").asDouble(), value.path("sw").asInt(), value.path("sh").asInt(),
            value.path("vw").asInt(), value.path("vh").asInt(), value.path("dpr").asDouble(),
        )
    }

    private fun evaluate(expression: String, deadline: Long): JsonNode {
        val params = json.writeValueAsString(mapOf("expression" to expression, "awaitPromise" to true, "returnByValue" to true))
        val value = json.readTree(await(devTools.call("Runtime.evaluate", params), deadline)).path("result").path("value")
        check(!value.isMissingNode) { "no evaluate result" }
        return value
    }

    private fun viewportPng(plan: ShotPlan, deadline: Long): ByteArray {
        val clip = plan.clip?.let { ",\"clip\":{\"x\":${num(it.x)},\"y\":${num(it.y)},\"width\":${num(it.width)},\"height\":${num(it.height)},\"scale\":${num(plan.scale)}}" }.orEmpty()
        val response = await(devTools.call("Page.captureScreenshot", "{\"format\":\"png\",\"fromSurface\":true,\"captureBeyondViewport\":false$clip}"), deadline)
        return Base64.getDecoder().decode(json.readTree(response).path("data").asString())
    }

    /** Viewport-sized slices drawn at their real scroll offset, scaled to the plan; a clamped last scroll just overdraws identical pixels. */
    private fun stitch(plan: ShotPlan, metrics: PageMetrics, started: PageState, deadline: Long): Image {
        val clip = plan.clip!!
        val unit = plan.scale * metrics.dpr
        return Surface.makeRasterN32Premul((clip.width * unit).roundToInt(), (clip.height * unit).roundToInt()).use { surface ->
            var top = 0.0
            while (top < clip.height) {
                var left = 0.0
                while (left < clip.width) {
                    ensureSamePage(started)
                    val at = evaluate(scrollScript(left, top), deadline)
                    Image.makeFromEncoded(viewportPng(VIEWPORT, deadline)).use { slice ->
                        val src = Rect.makeXYWH(0f, 0f, slice.width.toFloat(), slice.height.toFloat())
                        val dst = Rect.makeXYWH((at.path(0).asDouble() * unit).toFloat(), (at.path(1).asDouble() * unit).toFloat(), (slice.width * plan.scale).toFloat(), (slice.height * plan.scale).toFloat())
                        surface.canvas.drawImageRect(slice, src, dst)
                    }
                    left += metrics.viewWidth
                }
                top += metrics.viewHeight
            }
            surface.makeImageSnapshot()
        }
    }

    private fun encode(image: Image, plan: ShotPlan, metrics: PageMetrics, limits: ShotLimits): PageShot {
        var bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        if (bytes.size > limits.maxBytes()) bytes = image.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)!!.bytes
        val cssWidth = plan.clip?.width ?: metrics.viewWidth.toDouble()
        val cssHeight = plan.clip?.height ?: metrics.viewHeight.toDouble()
        return PageShot(bytes, image.width, image.height, cssWidth, cssHeight, plan.scale, plan.cut)
    }

    /** Best effort and bounded on its own: the capture deadline may already be spent. */
    private fun restore() {
        try {
            val params = json.writeValueAsString(mapOf("expression" to RESTORE_SCRIPT))
            devTools.call("Runtime.evaluate", params).get(RESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.warn("Could not restore the page after a screenshot: {}", e.javaClass.simpleName)
        }
    }

    private fun await(future: CompletableFuture<String>, deadline: Long): String =
        future.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)

    private fun num(value: Double) = String.format(Locale.ROOT, "%.4f", value)

    private companion object {
        val log = org.slf4j.LoggerFactory.getLogger(PageShooter::class.java)
        val VIEWPORT = ShotPlan(null, 1.0, false, false)
        const val JPEG_QUALITY = 85
        const val RESTORE_TIMEOUT_SECONDS = 2L
        const val NEXT_FRAMES = "await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));"

        /** Only a literal flag or number is spliced into the scripts; nothing page-controlled reaches them. A stale record only lends its saved overlay state. */
        fun prepareScript(whole: Boolean) = """
            (async () => {
              const root = document.getElementById('__ccm-root');
              const old = window.__ccmShot;
              window.__ccmShot = {
                sx: scrollX, sy: scrollY,
                d: old ? old.d : (root ? root.style.getPropertyValue('display') : ''),
                p: old ? old.p : (root ? root.style.getPropertyPriority('display') : ''),
              };
              if (root) root.style.setProperty('display', 'none', 'important');
              if ($whole) window.scrollTo({ left: 0, top: 0, behavior: 'instant' });
              $NEXT_FRAMES
              const e = document.documentElement, b = document.body;
              return { sx: scrollX, sy: scrollY, sw: Math.max(e.scrollWidth, b ? b.scrollWidth : 0), sh: Math.max(e.scrollHeight, b ? b.scrollHeight : 0),
                       vw: innerWidth, vh: innerHeight, dpr: devicePixelRatio };
            })()
        """.trimIndent()

        fun scrollScript(left: Double, top: Double) =
            "(async () => { window.scrollTo({ left: ${ceil(left).toLong()}, top: ${ceil(top).toLong()}, behavior: 'instant' }); $NEXT_FRAMES return [scrollX, scrollY]; })()"

        const val RESTORE_SCRIPT = """
            (() => {
              const s = window.__ccmShot; if (!s) return;
              delete window.__ccmShot;
              const root = document.getElementById('__ccm-root');
              if (root) { if (s.d) root.style.setProperty('display', s.d, s.p); else root.style.removeProperty('display'); }
              window.scrollTo({ left: s.sx, top: s.sy, behavior: 'instant' });
            })()
        """
    }
}
