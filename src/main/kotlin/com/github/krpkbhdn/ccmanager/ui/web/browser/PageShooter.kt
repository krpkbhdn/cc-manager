package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
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
 * When the document itself cannot scroll (an SPA), the biggest inner scroll container is stitched instead: its client rect from each slice, so
 * the result is that container's full content without the page chrome around it.
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
        val viewWidth = value.path("vw").asInt()
        val viewHeight = value.path("vh").asInt()
        val c = value.path("c")
        val container = ScrollContainer(c.path("x").asDouble(), c.path("y").asDouble(), c.path("w").asDouble(), c.path("h").asDouble())
            .takeIf { c.isObject && it.isUsableIn(viewWidth, viewHeight) }
        val sizeOf = if (container != null) c else value
        return PageMetrics(
            value.path("sx").asDouble(), value.path("sy").asDouble(), sizeOf.path("sw").asInt(), sizeOf.path("sh").asInt(),
            viewWidth, viewHeight, value.path("dpr").asDouble(), container,
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

    /**
     * Viewport-sized slices drawn at their real scroll offset on whole pixels (a fractional offset would blur text) and, at scale 1, copied
     * 1:1 without resampling; only a plan below 1 resamples, with a mipmapped linear filter. A clamped last scroll just overdraws identical pixels.
     * Peak memory: the surface (4 bytes per output pixel, at most `maxPixels`) plus one viewport slice, then the PNG bytes of the snapshot.
     */
    private fun stitch(plan: ShotPlan, metrics: PageMetrics, started: PageState, deadline: Long): Image {
        val clip = plan.clip!!
        val unit = plan.scale * metrics.dpr
        val container = metrics.container
        val sampling = if (plan.scale < 1.0) FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR) else SamplingMode.DEFAULT
        val stepWidth = container?.width ?: metrics.viewWidth.toDouble()
        val stepHeight = container?.height ?: metrics.viewHeight.toDouble()
        return Surface.makeRasterN32Premul((clip.width * unit).roundToInt(), (clip.height * unit).roundToInt()).use { surface ->
            var top = 0.0
            while (top < clip.height) {
                var left = 0.0
                while (left < clip.width) {
                    ensureSamePage(started)
                    val at = evaluate(scrollScript(left, top, container != null, top == 0.0 && left == 0.0, top + stepHeight >= clip.height), deadline)
                    Image.makeFromEncoded(viewportPng(VIEWPORT, deadline)).use { slice ->
                        val src = container?.let { Rect.makeXYWH((it.x * metrics.dpr).toFloat(), (it.y * metrics.dpr).toFloat(), (it.width * metrics.dpr).toFloat(), (it.height * metrics.dpr).toFloat()) }
                            ?: Rect.makeXYWH(0f, 0f, slice.width.toFloat(), slice.height.toFloat())
                        val dst = Rect.makeXYWH(Math.round(at.path(0).asDouble() * unit).toFloat(), Math.round(at.path(1).asDouble() * unit).toFloat(), (src.width * plan.scale).toFloat(), (src.height * plan.scale).toFloat())
                        surface.canvas.drawImageRect(slice, src, dst, sampling, null, true)
                    }
                    left += stepWidth
                }
                top += stepHeight
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
        const val JPEG_QUALITY = 95
        const val RESTORE_TIMEOUT_SECONDS = 2L
        const val MAX_SCANNED = 20_000
        const val NEXT_FRAMES = "await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));"

        /** Only a literal flag or number is spliced into the scripts; nothing page-controlled reaches them. A stale record only lends its saved overlay state. */
        fun prepareScript(whole: Boolean) = """
            (async () => {
              $MAIN_SCROLLER
              $FIXED_ELEMENTS
              const root = document.getElementById('__ccm-root');
              const old = window.__ccmShot;
              window.__ccmShot = {
                sx: scrollX, sy: scrollY,
                d: old ? old.d : (root ? root.style.getPropertyValue('display') : ''),
                p: old ? old.p : (root ? root.style.getPropertyPriority('display') : ''),
                el: old ? old.el : null, l: old ? old.l : 0, t: old ? old.t : 0,
                fx: old && old.fx ? old.fx : ($whole ? fixedElements() : []),
              };
              if (root) root.style.setProperty('display', 'none', 'important');
              if ($whole) window.scrollTo({ left: 0, top: 0, behavior: 'instant' });
              $NEXT_FRAMES
              const e = document.documentElement, b = document.body;
              const vo = getComputedStyle(e).overflowX !== 'visible' || !b ? getComputedStyle(e).overflowX : getComputedStyle(b).overflowX;
              const clipsX = /hidden|clip/.test(vo);
              const out = { sx: scrollX, sy: scrollY, sw: clipsX ? e.clientWidth : Math.max(e.scrollWidth, b ? b.scrollWidth : 0), sh: Math.max(e.scrollHeight, b ? b.scrollHeight : 0),
                            vw: innerWidth, vh: innerHeight, dpr: devicePixelRatio };
              const se = document.scrollingElement || e;
              if ($whole && se.scrollHeight <= se.clientHeight + 1 && se.scrollWidth <= se.clientWidth + 1) {
                const el = mainScroller();
                if (el) {
                  if (!window.__ccmShot.el) { window.__ccmShot.el = el; window.__ccmShot.l = el.scrollLeft; window.__ccmShot.t = el.scrollTop; }
                  el.scrollTo({ left: 0, top: 0, behavior: 'instant' });
                  $NEXT_FRAMES
                  const r = el.getBoundingClientRect();
                  const cs = getComputedStyle(el), scrolls = (v) => /auto|scroll|overlay/.test(v);
                  out.c = { x: r.left + el.clientLeft, y: r.top + el.clientTop, w: el.clientWidth, h: el.clientHeight,
                            sw: scrolls(cs.overflowX) ? el.scrollWidth : el.clientWidth, sh: scrolls(cs.overflowY) ? el.scrollHeight : el.clientHeight };
                }
              }
              return out;
            })()
        """.trimIndent()

        /** An SPA scrolls an inner element, not the document: the visible scrollable element with the biggest client area, then the biggest content. */
        const val MAIN_SCROLLER = """
            function mainScroller() {
              let best = null, bestArea = 0, bestExtent = 0, seen = 0;
              const walk = (node) => {
                for (const el of node.querySelectorAll('*')) {
                  if (++seen > $MAX_SCANNED) return;
                  if (el.shadowRoot) walk(el.shadowRoot);
                  const vert = el.scrollHeight > el.clientHeight + 1, horiz = el.scrollWidth > el.clientWidth + 1;
                  if (!vert && !horiz) continue;
                  const s = getComputedStyle(el);
                  if (s.visibility === 'hidden' || !((vert && /auto|scroll|overlay/.test(s.overflowY)) || (horiz && /auto|scroll|overlay/.test(s.overflowX)))) continue;
                  const area = el.clientWidth * el.clientHeight, extent = el.scrollWidth * el.scrollHeight;
                  if (area > 0 && (area > bestArea || (area === bestArea && extent > bestExtent))) { best = el; bestArea = area; bestExtent = extent; }
                }
              }
              walk(document);
              return best;
            }
        """

        /** Fixed and sticky elements with the half of the viewport they sit in and their own visibility, read before any is hidden. */
        const val FIXED_ELEMENTS = """
            function fixedElements() {
              const out = []; let seen = 0;
              const walk = (node) => {
                for (const el of node.querySelectorAll('*')) {
                  if (++seen > $MAX_SCANNED) return;
                  if (el.shadowRoot) walk(el.shadowRoot);
                  if (el.id === '__ccm-root') continue;
                  const pos = getComputedStyle(el).position;
                  if (pos !== 'fixed' && pos !== 'sticky') continue;
                  const r = el.getBoundingClientRect();
                  out.push({ el, top: r.top + r.height / 2 < innerHeight / 2, v: el.style.getPropertyValue('visibility'), p: el.style.getPropertyPriority('visibility') });
                }
              }
              walk(document);
              return out;
            }
        """

        /** A fixed or sticky element is photographed once: top ones in the first slice, bottom ones in the last row, hidden elsewhere. */
        fun scrollScript(left: Double, top: Double, container: Boolean, first: Boolean, lastRow: Boolean): String {
            val target = if (container) "window.__ccmShot.el" else "window"
            val read = if (container) "[el.scrollLeft, el.scrollTop]" else "[scrollX, scrollY]"
            val firstColumn = left == 0.0
            val showTop = first
            val showBottom = lastRow && firstColumn
            val fixed = "for (const f of window.__ccmShot.fx) f.el.style.setProperty('visibility', (f.top ? $showTop : $showBottom) ? f.v : 'hidden', (f.top ? $showTop : $showBottom) ? f.p : 'important');"
            return "(async () => { $fixed const el = $target; el.scrollTo({ left: ${ceil(left).toLong()}, top: ${ceil(top).toLong()}, behavior: 'instant' }); $NEXT_FRAMES return $read; })()"
        }

        const val RESTORE_SCRIPT = """
            (() => {
              const s = window.__ccmShot; if (!s) return;
              delete window.__ccmShot;
              const root = document.getElementById('__ccm-root');
              if (root) { if (s.d) root.style.setProperty('display', s.d, s.p); else root.style.removeProperty('display'); }
              for (const f of s.fx || []) { if (f.v) f.el.style.setProperty('visibility', f.v, f.p); else f.el.style.removeProperty('visibility'); }
              if (s.el) s.el.scrollTo({ left: s.l, top: s.t, behavior: 'instant' });
              window.scrollTo({ left: s.sx, top: s.sy, behavior: 'instant' });
            })()
        """
    }
}
