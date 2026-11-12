package com.github.krpkbhdn.ccmanager.ui.web

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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Similar-element and contrast modules in the real Chromium: `CCM_REAL_JCEF=1 ./gradlew test --tests '*InspectorSimilarRealTest'`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorSimilarRealTest {
    private val timeoutMillis = 10_000L
    private val json = JsonMapper.builder().build()
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-similar-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.copy(Path.of("src/test/resources/web-review/fixtures/similar.html"), dir.resolve("similar.html")).toUri().toString()
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

    private fun JsonNode.strings(): List<String> = (0 until size()).map { get(it).asString() }

    private fun describeRow(top: Int) = evaluate("window.__ccm.describeAt(20, ${top + 15})")

    @Test
    fun hashed_classes_are_dropped_and_identical_signatures_counted() {
        val first = describeRow(0)

        assertThat(first.get("stableClasses").strings()).containsExactly("card")
        assertThat(first.get("similarCount").asInt()).isEqualTo(2)
        assertThat(describeRow(120).get("stableClasses").strings()).containsExactly("card", "wide")
        assertThat(describeRow(120).get("similarCount").asInt()).isZero()
    }

    @Test
    fun elements_with_only_generated_classes_group_by_their_full_class_list() {
        val hashed = describeRow(160)

        assertThat(hashed.get("stableClasses")).isEmpty()
        assertThat(hashed.get("similarCount").asInt()).isEqualTo(1)
    }

    @Test
    fun a_classless_element_has_no_similar_elements_and_no_ancestor_group() {
        val plain = describeRow(480)

        assertThat(plain.get("similarCount").asInt()).isZero()
        assertThat(evaluate("window.__ccm.showSimilar('#plain1')").asInt()).isZero()
        evaluate("window.__ccm.showSimilar('') || 1")
    }

    @Test
    fun show_similar_counts_the_same_group_as_describe() {
        assertThat(evaluate("window.__ccm.showSimilar('#a')").asInt()).isEqualTo(describeRow(0).get("similarCount").asInt())
        assertThat(evaluate("window.__ccm.showSimilar('#h1')").asInt()).isEqualTo(describeRow(160).get("similarCount").asInt())
        evaluate("window.__ccm.showSimilar('') || 1")
    }

    @Test
    fun show_similar_pins_the_highlight_and_an_empty_selector_clears_it() {
        countStrokes {
            assertThat(evaluate("window.__ccm.showSimilar('#a')").asInt()).isEqualTo(2)
            Thread.sleep(400)
            assertThat(evaluate("window.__sr").asInt()).isPositive()

            assertThat(evaluate("window.__ccm.showSimilar('')").asInt()).isZero()
            Thread.sleep(400)
            evaluate("(window.__sr = 0, 1)")
            evaluate("window.__ccm.showSimilar('')")
            Thread.sleep(400)
            assertThat(evaluate("window.__sr").asInt()).isZero()
        }
    }

    private fun countStrokes(body: () -> Unit) {
        evaluate("(window.__sr = 0, window.__o = CanvasRenderingContext2D.prototype.stroke, CanvasRenderingContext2D.prototype.stroke = function () { window.__sr++; return window.__o.apply(this, arguments); }, 1)")
        try {
            body()
        } finally {
            evaluate("(CanvasRenderingContext2D.prototype.stroke = window.__o, 1)")
        }
    }

    @Test
    fun one_hover_change_scans_for_similar_elements_once() {
        evaluate("window.__ccm.showSimilar('') || 1")
        evaluate("window.__ccm.setOptions({similar: true, showBox: true, pick: false, showLabels: false, showAll: false}) || 1")
        host.mouse(java.awt.event.MouseEvent.MOUSE_MOVED, 500, 500, java.awt.event.MouseEvent.NOBUTTON, 0, 0)
        Thread.sleep(400)
        val before = evaluate("(window.__ccm.stats().similarScans || {draws: 0}).draws").asInt()
        host.mouse(java.awt.event.MouseEvent.MOUSE_MOVED, 20, 15, java.awt.event.MouseEvent.NOBUTTON, 0, 0)
        Thread.sleep(500)

        assertThat(evaluate("window.__ccm.stats().similarScans.draws").asInt() - before).isEqualTo(1)
        evaluate("window.__ccm.setOptions({similar: false, showBox: false}) || 1")
    }

    @Test
    fun hovering_a_card_redraws_the_similar_layer_with_only_similar_on() {
        countStrokes {
            evaluate("window.__ccm.showSimilar('') || 1")
            evaluate("window.__ccm.setOptions({similar: true, pick: false, showBox: false, showLabels: false, showAll: false}) || 1")
            try {
                for (x in listOf(500, 300, 100)) {
                    host.mouse(java.awt.event.MouseEvent.MOUSE_MOVED, x, x, java.awt.event.MouseEvent.NOBUTTON, 0, 0)
                    Thread.sleep(100)
                }
                Thread.sleep(300)
                evaluate("(window.__sr = 0, 1)")
                host.mouse(java.awt.event.MouseEvent.MOUSE_MOVED, 20, 15, java.awt.event.MouseEvent.NOBUTTON, 0, 0)
                Thread.sleep(500)

                assertThat(evaluate("window.__sr").asInt()).isPositive()
            } finally {
                evaluate("window.__ccm.setOptions({similar: false}) || 1")
            }
        }
    }

    @Test
    fun grey_777_on_white_fails_aa() {
        val contrast = describeRow(240).get("contrast")

        assertThat(contrast.get("ratio").asDouble()).isEqualTo(4.48)
        assertThat(contrast.get("aa").asBoolean()).isFalse()
        assertThat(contrast.get("aaa").asBoolean()).isFalse()
        assertThat(contrast.get("fg").asString()).isEqualTo("#777777")
        assertThat(contrast.get("bg").asString()).isEqualTo("#ffffff")
    }

    @Test
    fun grey_767676_on_white_passes_aa_only() {
        val contrast = describeRow(280).get("contrast")

        assertThat(contrast.get("aa").asBoolean()).isTrue()
        assertThat(contrast.get("aaa").asBoolean()).isFalse()
    }

    @Test
    fun large_text_uses_the_lower_thresholds() {
        val contrast = describeRow(360).get("contrast")

        assertThat(contrast.get("large").asBoolean()).isTrue()
        assertThat(contrast.get("aa").asBoolean()).isTrue()
        assertThat(contrast.get("aaa").asBoolean()).isFalse()
    }

    @Test
    fun white_on_black_is_21() {
        assertThat(describeRow(400).get("contrast").get("ratio").asDouble()).isEqualTo(21.0)
    }

    @Test
    fun a_gradient_background_or_missing_text_gives_no_contrast() {
        assertThat(describeRow(320).get("contrast").isNull).isTrue()
        assertThat(describeRow(440).get("contrast").isNull).isTrue()
    }
}
