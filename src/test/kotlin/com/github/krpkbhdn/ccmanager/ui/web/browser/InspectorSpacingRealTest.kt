package com.github.krpkbhdn.ccmanager.ui.web.browser

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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs the real Chromium: `CCM_REAL_JCEF=1 ./gradlew test --tests '*InspectorSpacingRealTest'`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorSpacingRealTest {
    private val timeoutMillis = 10_000L
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()
    private lateinit var debug: String

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        val dir = Files.createDirectories(Path.of("build/jcef-spacing-test"))
        val html = javaClass.getResource("/web-review/fixtures/spacing.html")!!.readText()
        val url = Files.writeString(dir.resolve("spacing.html"), html).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(640, 480, 1f)
        host.load(url)
        val end = System.currentTimeMillis() + timeoutMillis
        while ((host.page.value.url != url || host.page.value.loading) && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(500)
        host.runScript(
            "window.__ccm.setOptions({spacing: ['MARGIN','PADDING','GAP','VALUES']});" +
                "cefQuery({request: 'spacing=' + JSON.stringify(window.__ccm.spacingDebug())})",
        )
        debug = generateSequence { events.poll(timeoutMillis, TimeUnit.MILLISECONDS) }
            .filterIsInstance<PageEvent.Inspector>().map { it.json }.firstOrNull { it.startsWith("spacing=") }
            .orEmpty()
    }

    @AfterAll
    fun stopBrowser() {
        if (!::host.isInitialized) return
        scope.cancel()
        host.close()
        JcefRuntime.shutdown()
    }

    private fun region(id: String, kind: String, side: String, px: Number, label: String) =
        """{"id":"$id","kind":"$kind","side":"$side","px":$px,"label":"$label"}"""

    @Test
    fun margin_shows_the_authored_rem() {
        assertThat(debug).contains(region("m", "MARGIN", "top", 16, "1rem"))
    }

    @Test
    fun padding_shows_the_authored_px() {
        assertThat(debug).contains(region("p", "PADDING", "left", 20, "20px"))
    }

    @Test
    fun flex_gap_shows_the_authored_em_between_children() {
        assertThat(debug).contains(region("g", "GAP", "column", 20, "2em"))
    }

    @Test
    fun percent_padding_shows_the_authored_percent() {
        assertThat(debug).contains(region("pc", "PADDING", "left", 40, "10%"))
    }

    @Test
    fun a_custom_property_shows_its_token_name() {
        assertThat(debug).contains(region("v", "PADDING", "left", 18, "--space"))
    }

    @Test
    fun inline_style_shows_its_px() {
        assertThat(debug).contains(region("i", "MARGIN", "top", 20, "20px"))
    }

    @Test
    fun a_calc_value_falls_back_to_computed_px() {
        assertThat(debug).contains(region("calc", "MARGIN", "left", 19, "19px"))
    }
}
