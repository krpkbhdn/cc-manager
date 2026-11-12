package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefPageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.JcefRuntime
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.WebPageHost
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

private const val WIDTH = 400
private const val HEIGHT = 300
private const val FRAME_NANOS = 16_000_000L
private const val TIMEOUT_MILLIS = 10_000L

private const val PAGE = """<!doctype html><html><body style="margin:0">
<div id="box" style="position:absolute;left:20px;top:20px;width:100px;height:100px;background:#ccc"></div>
<div id="inner" style="position:absolute;left:200px;top:20px;width:100px;height:100px;overflow:auto"><div style="height:1000px"></div></div>
<div id="clip" style="position:absolute;left:20px;top:150px;width:150px;height:100px;overflow-x:hidden;overflow-y:auto"><div style="position:absolute;left:400px;top:0;width:600px;height:50px;background:#f0f"></div><div style="height:1000px"></div></div>
<div style="height:3000px"></div>
<script>window.__mouse = 0;
['mousemove','mousedown','mouseup','click','wheel'].forEach(function (t) { document.addEventListener(t, function () { window.__mouse++; }, true); });</script>
</body></html>"""

/** A locked canvas over the real Chromium: `CCM_REAL_JCEF=1 ./gradlew test --tests '*LockedCanvasRealTest' --rerun`. */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LockedCanvasRealTest {
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()
    private var nanos = 0L

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/locked-canvas-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.writeString(dir.resolve("page.html"), PAGE).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(WIDTH, HEIGHT, 1f)
        host.load(page)
        val end = System.currentTimeMillis() + TIMEOUT_MILLIS
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

    private fun evaluate(expression: String): String {
        host.runScript("cefQuery({request: 'ev=' + JSON.stringify($expression)})")
        val end = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.startsWith("ev=")) return event.json.removePrefix("ev=")
        }
        error("no answer for $expression")
    }

    private fun frames(scene: ImageComposeScene, count: Int) = repeat(count) {
        nanos += FRAME_NANOS
        scene.render(nanos)
        Thread.sleep(20)
    }

    @Test
    fun a_locked_canvas_delivers_no_mouse_events_and_no_hover_while_the_inspector_still_picks() {
        host.runScript(InspectorBridge.setOptions(InspectorToggles(pick = true).effective()))
        events.clear()
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { WebCanvas(host, PageViewport(WIDTH, HEIGHT), Modifier, interaction = false) }
        try {
            frames(scene, 5)
            val at = Offset(60f, 60f)
            scene.sendPointerEvent(PointerEventType.Move, at)
            frames(scene, 2)
            scene.sendPointerEvent(PointerEventType.Move, Offset(61f, 61f))
            frames(scene, 3)
            scene.sendPointerEvent(PointerEventType.Press, at, timeMillis = 10, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = 20, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Scroll, at, Offset(0f, 1f), timeMillis = 30)
            frames(scene, 5)

            val types = generateSequence { events.poll(500, TimeUnit.MILLISECONDS) }.filterIsInstance<PageEvent.Inspector>().map { it.json }.toList()
            assertThat(types).anyMatch { it.contains("\"type\":\"pick\"") }
            assertThat(types).anyMatch { it.contains("\"type\":\"hover\"") }
            assertThat(evaluate("window.__mouse")).isEqualTo("0")
            assertThat(evaluate("document.querySelector('#box').matches(':hover')")).isEqualTo("false")
            val end = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (evaluate("window.scrollY") == "0" && System.currentTimeMillis() < end) Thread.sleep(100)
            assertThat(evaluate("window.scrollY")).isNotEqualTo("0")
        } finally {
            scene.close()
        }
    }

    @Test
    fun a_locked_wheel_over_a_nested_scroller_scrolls_that_element() {
        host.runScript("window.scrollTo(0, 0)")
        assertThat(evaluate("window.scrollY")).isEqualTo("0")
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { WebCanvas(host, PageViewport(WIDTH, HEIGHT), Modifier, interaction = false) }
        try {
            frames(scene, 5)
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(250f, 60f), Offset(0f, 1f), timeMillis = 40)
            frames(scene, 5)
            val end = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (evaluate("document.querySelector('#inner').scrollTop") == "0" && System.currentTimeMillis() < end) Thread.sleep(100)
            assertThat(evaluate("document.querySelector('#inner').scrollTop")).isNotEqualTo("0")
        } finally {
            scene.close()
        }
    }

    @Test
    fun a_locked_wheel_with_a_horizontal_delta_never_scrolls_a_clipped_axis_and_a_whole_page_shot_leaves_it_alone() {
        host.runScript("window.scrollTo(0, 0)")
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { WebCanvas(host, PageViewport(WIDTH, HEIGHT), Modifier, interaction = false) }
        try {
            frames(scene, 5)
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(60f, 200f), Offset(3f, 1f), timeMillis = 40)
            frames(scene, 5)
            val end = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (evaluate("document.querySelector('#clip').scrollTop") == "0" && System.currentTimeMillis() < end) Thread.sleep(100)
            assertThat(evaluate("document.querySelector('#clip').scrollTop")).isNotEqualTo("0")
            assertThat(evaluate("document.querySelector('#clip').scrollLeft")).isEqualTo("0")
            assertThat(evaluate("window.scrollX")).isEqualTo("0")

            val shot = host.screenshot(com.github.krpkbhdn.ccmanager.ui.web.browser.ShotArea.WholePage, com.github.krpkbhdn.ccmanager.ui.web.browser.ShotLimits(8192, 36_000_000, 20_000_000))!!

            assertThat(shot.widthPx.toString()).isEqualTo(evaluate("document.documentElement.clientWidth"))
            assertThat(evaluate("document.querySelector('#clip').scrollLeft")).isEqualTo("0")
        } finally {
            scene.close()
        }
    }
}
