package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefPageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefRuntime
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
import java.awt.Canvas
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
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

/** Pin fan-out and the outline overlay as painted pixels: `CCM_REAL_JCEF=1 ./gradlew test --tests '*InspectorPinsRealTest'`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorPinsRealTest {
    private val timeoutMillis = 10_000L
    private val json = JsonMapper.builder().build()
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()
    private val awtSource = Canvas()
    private val rawCode = KeyEvent::class.java.getDeclaredField("rawCode").apply { isAccessible = true }

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-pins-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.writeString(dir.resolve("pins.html"), PAGE).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(WIDTH, HEIGHT, 1f)
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

    private fun screenshot(): BufferedImage {
        Thread.sleep(500)
        val png = checkNotNull(host.capture(0, 0, WIDTH, HEIGHT)) { "no frame captured" }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun BufferedImage.countNotWhite(xDip: IntRange, yDip: IntRange): Int {
        val scale = width.toDouble() / WIDTH
        var n = 0
        for (x in (xDip.first * scale).toInt()..(xDip.last * scale).toInt()) for (y in (yDip.first * scale).toInt()..(yDip.last * scale).toInt()) {
            if ((getRGB(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1)) and 0xffffff) != 0xffffff) n++
        }
        return n
    }

    private fun BufferedImage.countNear(rgb: Int, xDip: IntRange, yDip: IntRange): Int {
        val scale = width.toDouble() / WIDTH
        var n = 0
        for (x in (xDip.first * scale).toInt()..(xDip.last * scale).toInt()) for (y in (yDip.first * scale).toInt()..(yDip.last * scale).toInt()) {
            val p = getRGB(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
            if (abs(((p shr 16) and 0xff) - ((rgb shr 16) and 0xff)) < TOLERANCE && abs(((p shr 8) and 0xff) - ((rgb shr 8) and 0xff)) < TOLERANCE &&
                abs((p and 0xff) - (rgb and 0xff)) < TOLERANCE
            ) n++
        }
        return n
    }

    @Test
    fun two_pins_on_one_element_sit_side_by_side_and_both_stay_visible() {
        evaluate("window.__ccm.setOptions({showAll: false, similar: false}) || 1")
        evaluate(
            "window.__ccm.setAnnotations([{id: 'a', n: 1, status: 'open', anchor: {selector: '#t', rect: {x: 100, y: 100, w: 200, h: 100}}}," +
                "{id: 'b', n: 2, status: 'done', anchor: {selector: '#t', rect: {x: 100, y: 100, w: 200, h: 100}}}]) || 1",
        )
        val shot = screenshot()

        assertThat(shot.countNear(OPEN_PIN, 102..108, 102..108)).isGreaterThan(10)
        assertThat(shot.countNear(DONE_PIN, 114..130, 103..108)).isGreaterThan(20)
        assertThat(shot.countNear(DONE_PIN, 102..108, 102..108)).isZero()
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    private fun pins(vararg pins: Triple<String, Int, String>, x: Int = 100, y: Int = 100): String =
        pins.joinToString(",", "[", "]") { (id, n, status) ->
            "{id: '$id', n: $n, status: '$status', anchor: {selector: '#t', rect: {x: $x, y: $y, w: 200, h: 100}}}"
        }

    @Test
    fun the_seventh_pin_on_one_element_wraps_to_a_second_row() {
        evaluate("window.__ccm.setOptions({showAll: false, similar: false}) || 1")
        val six = (1..6).map { Triple("p$it", it, "open") }.toTypedArray()
        evaluate("window.__ccm.setAnnotations(" + pins(*six, Triple("p7", 7, "done")) + ") || 1")
        val shot = screenshot()

        assertThat(shot.countNear(OPEN_PIN, 204..216, 94..106)).isGreaterThan(10)
        assertThat(shot.countNear(DONE_PIN, 92..108, 118..128)).isGreaterThan(10)
        assertThat(shot.countNear(DONE_PIN, 88..235, 88..98)).isZero()
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    @Test
    fun a_pin_at_the_page_edge_is_clamped_into_the_viewport() {
        evaluate("window.__ccm.setOptions({showAll: false, similar: false}) || 1")
        evaluate("(function(t){t.style.left = '630px'; t.style.top = '5px'; return 1})(document.getElementById('t'))")
        evaluate("window.__ccm.setAnnotations(" + pins(Triple("e", 1, "open"), x = 630, y = 5) + ") || 1")
        val shot = screenshot()
        evaluate("(function(t){t.style.left = '100px'; t.style.top = '100px'; return 1})(document.getElementById('t'))")

        assertThat(shot.countNear(OPEN_PIN, (WIDTH - 18)..(WIDTH - 2), 2..18)).isGreaterThan(20)
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    @Test
    fun pins_at_the_right_edge_fan_out_leftwards_instead_of_overlapping() {
        evaluate("window.__ccm.setOptions({showAll: false, similar: false}) || 1")
        evaluate("(function(t){t.style.left = '630px'; return 1})(document.getElementById('t'))")
        evaluate("window.__ccm.setAnnotations(" + pins(Triple("a", 1, "open"), Triple("b", 2, "done"), x = 630, y = 100) + ") || 1")
        val shot = screenshot()
        evaluate("(function(t){t.style.left = '100px'; return 1})(document.getElementById('t'))")

        assertThat(shot.countNear(OPEN_PIN, (WIDTH - 18)..(WIDTH - 2), 92..108)).isGreaterThan(20)
        assertThat(shot.countNear(DONE_PIN, (WIDTH - 42)..(WIDTH - 24), 92..108)).isGreaterThan(20)
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    @Test
    fun a_tall_element_scrolled_past_its_top_still_shows_its_pin() {
        evaluate("window.__ccm.setOptions({showAll: false, similar: false}) || 1")
        evaluate("(function(t){t.style.top = '-50px'; t.style.height = '300px'; return 1})(document.getElementById('t'))")
        evaluate("window.__ccm.setAnnotations(" + pins(Triple("a", 1, "open"), y = 100) + ") || 1")
        val shot = screenshot()
        evaluate("(function(t){t.style.top = '100px'; t.style.height = '100px'; return 1})(document.getElementById('t'))")

        assertThat(shot.countNear(OPEN_PIN, 100..120, 0..12)).isGreaterThan(10)
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    @Test
    fun space_is_not_default_handled_on_the_page_but_is_in_a_text_field() {
        evaluate("(window.__prevented = [], addEventListener('keydown', function (e) { setTimeout(function () { window.__prevented.push(e.defaultPrevented) }, 0) }), 1)")
        fun pressSpace() {
            events.clear()
            for (id in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
                val e = KeyEvent(awtSource, id, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ')
                rawCode.setLong(e, KeyEvent.VK_SPACE.toLong())
                host.key(e)
            }
            Thread.sleep(300)
        }
        evaluate("(document.body.focus(), 1)")
        pressSpace()
        evaluate("(function(i){i.focus(); return 1})(document.getElementById('f'))")
        pressSpace()

        assertThat(evaluate("window.__prevented").toString()).isEqualTo("[true,false]")
    }

    private fun messagesAfter(script: String): List<JsonNode> {
        events.clear()
        host.runScript(script)
        Thread.sleep(400)
        return generateSequence { events.poll() }.filterIsInstance<PageEvent.Inspector>().map { json.readTree(it.json) }.toList()
    }

    @Test
    fun pointer_api_drives_hover_pick_and_pins_without_real_mouse_events() {
        evaluate("window.__ccm.setOptions({pick: true, showAll: false}) || 1")
        evaluate("window.__ccm.setAnnotations(" + pins(Triple("a", 1, "open")) + ") || 1")

        val moved = messagesAfter(InspectorBridge.pointer("move", 200.0, 150.0))
        assertThat(moved.map { it["type"].asString() }).contains("hover")
        assertThat(evaluate("document.getElementById('t').matches(':hover')").asBoolean()).isFalse()

        val picked = messagesAfter(InspectorBridge.pointer("click", 200.0, 150.0))
        assertThat(picked.map { it["type"].asString() }).contains("pick")

        val onPin = messagesAfter(InspectorBridge.pointer("move", 100.0, 100.0))
        assertThat(onPin.map { it["type"].asString() }).contains("annotationHover")
        val pinClick = messagesAfter(InspectorBridge.pointer("click", 100.0, 100.0))
        assertThat(pinClick.map { it["type"].asString() }).contains("annotationClick").doesNotContain("pick")
        val left = messagesAfter(InspectorBridge.pointer("leave", 0.0, 0.0))
        assertThat(left.map { it["type"].asString() }).contains("annotationLeave")

        evaluate("window.__ccm.setOptions({pick: false}) || 1")
        assertThat(messagesAfter(InspectorBridge.pointer("click", 200.0, 150.0)).map { it["type"].asString() }).doesNotContain("pick")
        evaluate("window.__ccm.setAnnotations([]) || 1")
    }

    @Test
    fun editing_messages_are_sent() {
        assertThat(editingMessages("focusin")).containsExactly(true)
        assertThat(editingMessages("focusout")).containsExactly(false)
    }

    @Test
    fun only_real_text_entry_counts_as_editing() {
        fun editingAfterFocusin(id: String) = messagesAfter("document.getElementById('$id').dispatchEvent(new FocusEvent('focusin', {bubbles: true, composed: true}))")
            .filter { it["type"].asString() == "editing" }.map { it["on"].asBoolean() }
        assertThat(editingAfterFocusin("f")).containsExactly(true)
        assertThat(editingAfterFocusin("b")).containsExactly(false)
        assertThat(editingAfterFocusin("c")).isEmpty()
        assertThat(editingAfterFocusin("f")).containsExactly(true)
        assertThat(editingMessages("focusout")).containsExactly(false)
    }

    /** Dispatched, not focused: an offscreen browser has no window focus, so a real focus() would not raise the events. */
    private fun editingMessages(event: String): List<Boolean> =
        messagesAfter("document.getElementById('f').dispatchEvent(new FocusEvent('$event', {bubbles: true, composed: true}))")
            .filter { it["type"].asString() == "editing" }.map { it["on"].asBoolean() }

    private fun outlineIsPainted(density: Float) {
        host.resize(WIDTH, HEIGHT, density)
        Thread.sleep(500)
        assertThat(evaluate("window.devicePixelRatio").asDouble()).isEqualTo(density.toDouble())
        evaluate("window.__ccm.setOptions({showAll: true}) || 1")

        assertThat(screenshot().countNotWhite(96..104, 120..180)).isGreaterThan(20)
        evaluate("window.__ccm.setOptions({showAll: false}) || 1")
    }

    @Test
    fun outline_all_is_visible_at_device_pixel_ratio_1() = outlineIsPainted(1f)

    @Test
    fun outline_all_is_visible_at_device_pixel_ratio_2() = outlineIsPainted(2f)

    @Test
    fun layer_stats_count_every_draw() {
        evaluate("window.__ccm.setOptions({showAll: true}) || 1")
        Thread.sleep(300)

        assertThat(evaluate("window.__ccm.stats().all.draws").asInt()).isPositive()
        evaluate("window.__ccm.setOptions({showAll: false}) || 1")
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 520
        const val TOLERANCE = 30
        const val OPEN_PIN = 0xF2B93B
        const val DONE_PIN = 0x3DD68C
        const val PAGE = """<!doctype html><meta charset=utf-8>
<style>html,body{margin:0;background:#fff}#t{position:absolute;left:100px;top:100px;width:200px;height:100px;background:#fff}#t:hover{background:#f00}</style>
<div id="t"></div><input id="f" style="position:absolute;left:400px;top:400px"><button id="b">b</button><input id="c" type="checkbox">"""
    }
}
