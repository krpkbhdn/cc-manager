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

/** Layer redraw on scroll and generated selectors of control-character attributes: `CCM_REAL_JCEF=1 ./gradlew test --tests "*InspectorCoreRealTest"`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorCoreRealTest {
    private val timeoutMillis = 10_000L
    private val json = JsonMapper.builder().build()
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-core-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.writeString(dir.resolve("core.html"), PAGE).toUri().toString()
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

    @Test
    fun a_scroll_redraws_the_visible_layers() {
        evaluate("window.__ccm.setOptions({showAll: true}) || 1")
        Thread.sleep(400)
        evaluate("(window.__strokes = 0, 1)")

        evaluate("(window.scrollTo(0, 300), 1)")
        Thread.sleep(400)

        assertThat(evaluate("window.__strokes").asInt()).isPositive()
    }

    @Test
    fun a_newline_in_an_attribute_value_gives_a_selector_that_resolves_to_the_element() {
        val answer = evaluate("(function(){var s = window.__ccm.describeAt(20, 20).selector; return {sel: s, ok: document.querySelector(s) === document.querySelector('[aria-label]')};})()")

        assertThat(answer.get("sel").asString()).doesNotContain("\n")
        assertThat(answer.get("ok").asBoolean()).isTrue()
    }

    @Test
    fun a_zero_width_joiner_in_an_attribute_gives_a_raw_free_selector_that_resolves_and_parses() {
        val selector = evaluate("window.__ccm.describeAt(320, 70).selector").asString()
        val resolves = evaluate("window.__ccm.resolveSelector(" + json.writeValueAsString(selector) + ") === document.querySelector('[aria-label^=a]')").asBoolean()
        events.clear()
        host.runScript(InspectorBridge.describeSelector(selector, "feedback"))
        val end = System.currentTimeMillis() + timeoutMillis
        var message: InspectorMessage? = null
        while (message == null && System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.contains("\"inspect\"")) message = InspectorMessage.parse(event.json)
        }

        assertThat(selector).doesNotContain("\u200D").contains("aria-label")
        assertThat(resolves).isTrue()
        assertThat(message).isInstanceOf(InspectorMessage.Inspect::class.java)
    }

    private fun inspectMessage(selector: String): InspectorMessage? {
        events.clear()
        host.runScript(InspectorBridge.describeSelector(selector, "feedback"))
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.contains("\"inspect\"")) return InspectorMessage.parse(event.json)
        }
        return null
    }

    @Test
    fun every_character_the_host_rejects_is_escaped_in_generated_selectors() {
        for (x in listOf(420, 490)) {
            val selector = evaluate("window.__ccm.describeAt($x, 70).selector").asString()
            val clean = evaluate("!/[\\p{Cc}\\p{Cf}\\u2028\\u2029]/u.test(" + json.writeValueAsString(selector) + ")").asBoolean()
            val resolves = evaluate("window.__ccm.resolveSelector(" + json.writeValueAsString(selector) + ") === document.elementFromPoint($x, 70)").asBoolean()

            assertThat(clean).describedAs(selector).isTrue()
            assertThat(inspectMessage(selector)).describedAs(selector).isInstanceOf(InspectorMessage.Inspect::class.java)
            assertThat(resolves).describedAs(selector).isTrue()
        }
    }

    @Test
    fun a_selector_over_the_host_limit_is_never_sent() {
        evaluate(
            """(function () { var cur = document.body.appendChild(document.createElement('div'));
              cur.style.cssText = 'position:absolute;left:300px;top:300px;width:150px';
              for (var i = 0; i < 30; i++) {
                var a = document.createElement('div'), b = document.createElement('div');
                a.className = b.className = 'level-class-aaaaaaaaaaaaaaaa level-class-bbbbbbbbbbbbbbbb'; a.style.padding = '1px';
                cur.appendChild(a); cur.appendChild(b); cur = a; }
              cur.setAttribute('data-find', 'deep'); cur.style.minHeight = '10px'; return 1; })()""",
        )
        val length = evaluate("(function(){var r=document.querySelector('[data-find=deep]').getBoundingClientRect();return window.__ccm.describeAt(r.left+r.width/2,r.top+r.height/2).selector.length;})()").asInt()
        events.clear()
        host.runScript(InspectorBridge.describeSelector("[data-find=deep]", "feedback"))
        host.runScript(InspectorBridge.describeSelector("#shallow", "feedback"))
        Thread.sleep(1500)
        val sent = generateSequence { events.poll() }.filterIsInstance<PageEvent.Inspector>().map { it.json }.filter { it.contains("\"inspect\"") }.toList()

        assertThat(length).isGreaterThan(1000)
        assertThat(sent).hasSize(1)
        assertThat(sent.single()).contains("#shallow")
    }

    @Test
    fun the_text_of_an_element_skips_style_and_script_and_collapses_whitespace() {
        assertThat(evaluate("window.__ccm.describeAt(210, 10).text").asString()).isEqualTo("Hello world")
    }

    private fun hoverDrawCalls(options: String): Pair<Int, Int> {
        evaluate("window.__ccm.setOptions({showAll: false, showBox: false, showLabels: false, pick: false, $options}) || 1")
        evaluate("(window.__fills = 0, window.__texts = 0, 1)")
        for (x in listOf(500, 300, 50)) {
            host.mouse(MouseEvent.MOUSE_MOVED, x, x, MouseEvent.NOBUTTON, 0, 0)
            Thread.sleep(100)
        }
        Thread.sleep(500)
        return evaluate("window.__fills").asInt() to evaluate("window.__texts").asInt()
    }

    @Test
    fun box_model_alone_draws_the_hovered_element() {
        val (fills, texts) = hoverDrawCalls("showBox: true")

        assertThat(fills).isPositive()
        assertThat(texts).isZero()
    }

    @Test
    fun labels_alone_draw_the_name_of_the_hovered_element() {
        val (_, texts) = hoverDrawCalls("showLabels: true")

        assertThat(texts).isPositive()
    }

    private companion object {
        const val PAGE = """<!doctype html><meta charset=utf-8>
<script>window.__strokes = 0; var s0 = CanvasRenderingContext2D.prototype.stroke;
CanvasRenderingContext2D.prototype.stroke = function () { window.__strokes++; return s0.apply(this, arguments); };
window.__fills = 0; window.__texts = 0; var f0 = CanvasRenderingContext2D.prototype.fill, t0 = CanvasRenderingContext2D.prototype.fillText;
CanvasRenderingContext2D.prototype.fill = function () { window.__fills++; return f0.apply(this, arguments); };
CanvasRenderingContext2D.prototype.fillText = function () { window.__texts++; return t0.apply(this, arguments); };</script>
<style>body{margin:0;height:3000px}</style>
<div style="height:100px"><div aria-label="line1&#10;line2" style="position:absolute;left:0;top:0;width:100px;height:100px"></div></div>
<div aria-label="a&#8205;b" style="position:absolute;left:300px;top:50px;width:60px;height:40px"></div>
<div style="position:absolute;left:200px;top:0;width:100px;height:20px"><style>.x{color:red}</style><script>var q = 1;</script>  Hello
   world</div>
<div id="shallow" style="position:absolute;left:500px;top:300px;width:20px;height:20px"></div>
<div id="ctl1" style="position:absolute;left:400px;top:50px;width:60px;height:40px"></div>
<div id="ctl2" style="position:absolute;left:470px;top:50px;width:60px;height:40px"></div>
<script>var e1 = document.getElementById('ctl1'), e2 = document.getElementById('ctl2');
e1.removeAttribute('id'); e1.setAttribute('aria-label', 'a\u0085b\u2028c\u061Cd\u{E0001}e');
e2.id = 'i\u0085d'; e2.className = 'k\u061Cz';</script>
<div style="height:2000px">filler</div>
"""
    }
}
