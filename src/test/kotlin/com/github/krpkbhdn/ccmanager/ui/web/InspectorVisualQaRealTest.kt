package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefPageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefRuntime
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Overlay looks (outline all, spacing, audit borders, similar hover) as PNGs in build/web-qa: `CCM_REAL_JCEF=1 ./gradlew test --tests '*InspectorVisualQaRealTest'`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorVisualQaRealTest {
    private val timeoutMillis = 10_000L
    private val json = JsonMapper.builder().build()
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()
    private val out = Path.of("build/web-qa")

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-visual-test"))
        Files.createDirectories(out)
        val page = Files.writeString(dir.resolve("visual.html"), javaClass.getResource("/web-review/fixtures/visual.html")!!.readText()).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(640, 520, 1f)
        host.load(page)
        val end = System.currentTimeMillis() + timeoutMillis
        while ((host.page.value.url != page || host.page.value.loading) && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(500)
    }

    @AfterAll
    fun stopBrowser() {
        if (!::host.isInitialized) return
        scope.cancel()
        host.close()
        JcefRuntime.shutdown()
    }

    private fun evaluate(expression: String): JsonNode {
        events.clear()
        host.runScript("cefQuery({request: 'ev=' + JSON.stringify($expression)})")
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.startsWith("ev=")) return json.readTree(event.json.removePrefix("ev="))
        }
        error("no answer for $expression")
    }

    private fun options(js: String) {
        evaluate("window.__ccm.setOptions({pick:false,showAll:false,showBox:false,showLabels:false,similar:false,spacing:[],contrast:false, $js}) || 1")
    }

    private fun shot(name: String) {
        Thread.sleep(600)
        Files.write(out.resolve("$name.png"), host.capture(0, 0, 640, 520)!!)
    }

    private fun hoverOn(selector: String) {
        val box = evaluate("(function(){var r=document.querySelector('$selector').getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]})()")
        host.mouse(MouseEvent.MOUSE_MOVED, 600, 500, MouseEvent.NOBUTTON, 0, 0)
        Thread.sleep(150)
        host.mouse(MouseEvent.MOUSE_MOVED, box.get(0).asInt(), box.get(1).asInt(), MouseEvent.NOBUTTON, 0, 0)
    }

    /** Records fill styles, strokes and whether each roundRect had a non-zero radius while body runs; the canvas prototype is always restored. */
    private fun recording(body: () -> Unit): Recorded {
        evaluate(
            """(function () { var P = CanvasRenderingContext2D.prototype; window.__rp = {}; window.__r = {fills: [], strokes: 0, rounds: []};
              ['fill', 'stroke', 'roundRect'].forEach(function (m) { window.__rp[m] = P[m]; P[m] = function () {
                if (m === 'fill') window.__r.fills.push(String(this.fillStyle));
                if (m === 'stroke') window.__r.strokes++;
                if (m === 'roundRect') window.__r.rounds.push([].concat(arguments[4]).some(function (r) { return (r.x || r) > 0; }));
                return window.__rp[m].apply(this, arguments); }; }); return 1; })()""",
        )
        try {
            body()
            Thread.sleep(300)
            val r = evaluate("window.__r")
            return Recorded(
                r.get("fills").let { f -> (0 until f.size()).map { f.get(it).asString() } },
                r.get("strokes").asInt(),
                r.get("rounds").let { f -> (0 until f.size()).map { f.get(it).asBoolean() } },
            )
        } finally {
            evaluate("(['fill', 'stroke', 'roundRect'].forEach(function (m) { CanvasRenderingContext2D.prototype[m] = window.__rp[m]; }), 1)")
        }
    }

    private class Recorded(val fills: List<String>, val strokes: Int, val rounds: List<Boolean>) {
        fun maxFillAlpha() = fills.maxOf { Regex("""rgba\(.*,\s*([\d.]+)\)""").find(it)?.groupValues?.get(1)?.toDouble() ?: 1.0 }
    }

    @Test
    fun outline_all_draws_strokes_and_no_fill() {
        options("showAll:true")
        shot("1-outline-all")
        val recorded = recording { evaluate("window.__ccm.setOptions({showAll:true}) || 1") }

        assertThat(recorded.fills).isEmpty()
        assertThat(recorded.strokes).isPositive()
    }

    @Test
    fun spacing_regions_are_light_and_rounded() {
        options("spacing:['MARGIN','PADDING','GAP']")
        shot("2-spacing-all")
        val recorded = recording { evaluate("window.__ccm.setOptions({spacing:['MARGIN','PADDING','GAP']}) || 1") }

        assertThat(recorded.fills).isNotEmpty()
        assertThat(recorded.maxFillAlpha()).isLessThanOrEqualTo(.2)
        assertThat(recorded.rounds).contains(true)
    }

    @Test
    fun audit_borders_follow_corner_radii() {
        options("spacing:['BORDERS']")
        shot("3-audit-borders")
        val recorded = recording { evaluate("window.__ccm.setOptions({spacing:['BORDERS']}) || 1") }

        assertThat(recorded.strokes).isPositive()
        assertThat(recorded.rounds).contains(true)
    }

    @Test
    fun box_model_hover_rings_follow_corner_radii() {
        options("showBox:true")
        val recorded = recording {
            hoverOn("#c1")
            shot("4-hover-box")
        }

        assertThat(recorded.fills).isNotEmpty()
        assertThat(recorded.strokes).isPositive()
        assertThat(recorded.rounds).contains(true)
    }

    @Test
    fun similar_hover_paints_for_a_class_group_but_not_for_a_classless_element() {
        options("similar:true")
        val card = recording {
            hoverOn("#c1")
            shot("5-similar-card")
        }
        val inner = recording {
            hoverOn("#inner")
            shot("6-similar-classless-inner")
        }

        assertThat(card.strokes).isPositive()
        assertThat(inner.strokes).isZero()
    }
}
