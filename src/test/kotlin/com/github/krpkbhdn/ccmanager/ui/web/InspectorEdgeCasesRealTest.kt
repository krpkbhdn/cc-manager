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

/** Stable-class filter, layer stack and contrast edge cases: `CCM_REAL_JCEF=1 ./gradlew test --tests "*InspectorEdgeCasesRealTest"`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorEdgeCasesRealTest {
    private val timeoutMillis = 10_000L
    private val json = JsonMapper.builder().build()
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/inspector-edge-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val page = Files.writeString(dir.resolve("edge.html"), PAGE).toUri().toString()
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

    private fun stableCount(id: String): Int = evaluate("window.__ccm.showSimilar('#$id')").asInt() + 1

    private fun stackAt(x: Int, y: Int): List<String> {
        events.clear()
        host.runScript("window.__ccm.stackAt($x, $y)")
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.contains("\"stack\"")) {
                val items = json.readTree(event.json).get("items")
                return (0 until items.size()).map { items.get(it).get("selector").asString() }
            }
        }
        error("no stack answer")
    }

    @Test
    fun bem_elements_and_modifiers_are_stable_classes() {
        assertThat(stableCount("bem")).describedAs("card__title card__title--active").isEqualTo(2)
    }

    @Test
    fun tailwind_utilities_are_stable_classes() {
        assertThat(stableCount("tw1")).describedAs("w-[10px] md:flex").isEqualTo(2)
        assertThat(stableCount("tw2")).describedAs("p-4 m-4").isEqualTo(2)
        assertThat(stableCount("tw3")).describedAs("mt-10 h-16").isEqualTo(2)
    }

    @Test
    fun a_word_ending_in_hex_letters_is_not_a_hash() {
        assertThat(stableCount("hex")).describedAs("btn-added").isEqualTo(2)
    }

    @Test
    fun stack_lists_overlapped_elements_top_first_and_skips_pointer_events_none() {
        val stack = stackAt(50, 50)

        assertThat(stack).startsWith("#top", "#under").doesNotContain("#ghost")
    }

    @Test
    fun stack_descends_into_shadow_roots() {
        assertThat(stackAt(250, 50).joinToString("|")).contains("#host >>> div")
    }

    @Test
    fun contrast_blends_translucent_text_and_background_over_white() {
        val c = evaluate("window.__ccm.describeAt(20, 245)").get("contrast")

        assertThat(c.get("bg").asString()).isEqualTo("#808080")
        assertThat(c.get("ratio").asDouble()).isBetween(1.0, 21.0)
    }

    @Test
    fun transparent_background_falls_through_to_the_parent() {
        val c = evaluate("window.__ccm.describeAt(20, 285)").get("contrast")

        assertThat(c.get("bg").asString()).isEqualTo("#000000")
        assertThat(c.get("ratio").asDouble()).isEqualTo(21.0)
    }

    private companion object {
        const val PAGE = """<!doctype html><meta charset=utf-8><style>
body{margin:0} .r{position:absolute;left:0;width:200px;height:20px}
#top{position:absolute;left:0;top:0;width:100px;height:100px;background:#f00}
#under{position:absolute;left:0;top:0;width:200px;height:200px;background:#0f0}
#ghost{position:absolute;left:0;top:0;width:100px;height:100px;pointer-events:none}
#host{position:absolute;left:220px;top:0;width:100px;height:100px}
</style>
<div id=under></div><div id=top></div><div id=ghost></div><div id=host></div>
<script>var s=document.getElementById('host').attachShadow({mode:'open'});
s.innerHTML='<div class=shadow-inner style="width:100px;height:100px;background:#00f"></div>';</script>
<div style="position:absolute;top:400px">
<p id=bem class="card__title card__title--active">a</p><p class="card__title card__title--active">a</p>
<p id=tw1 class="w-[10px] md:flex">a</p><p class="w-[10px] md:flex">a</p>
<p id=tw2 class="p-4 m-4">a</p><p class="p-4 m-4">a</p>
<p id=tw3 class="mt-10 h-16">a</p><p class="mt-10 h-16">a</p>
<p id=hex class="btn-added">a</p><p class="btn-added">a</p></div>
<div style="position:absolute;top:230px;left:0;width:300px;background:rgba(0,0,0,.5)"><div style="height:30px;color:rgba(255,255,255,1)">t</div></div>
<div style="position:absolute;top:270px;left:0;width:300px;background:#000"><div style="height:30px;color:#fff;background:transparent">t</div></div>
"""
    }
}
