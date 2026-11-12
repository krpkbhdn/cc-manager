package com.github.krpkbhdn.ccmanager.ui.web.browser

import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.ui.web.ConsoleLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.jetbrains.skia.Image
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.net.InetAddress
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs the real Chromium: `CCM_REAL_JCEF=1 ./gradlew test --tests '*JcefPageHost*'` (JBR with JCEF, a few seconds, no network). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JcefPageHostRealTest {
    private val timeoutMillis = 10_000L
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()
    private lateinit var pageUrl: String
    private lateinit var dir: Path
    private val extraHosts = mutableListOf<JcefPageHost>()

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        // Not a @TempDir: CEF keeps its cache files locked until the JVM exits.
        dir = Files.createDirectories(Path.of("build/jcef-real-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        pageUrl = Files.writeString(dir.resolve("page.html"), PAGE).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(640, 480, 1f)
        host.focus(true)
        host.load(pageUrl)
        await("page loaded") { host.page.value.url == pageUrl && !host.page.value.loading && host.frame.value?.widthPx == 640 }
        Thread.sleep(500)
    }

    @AfterAll
    fun stopBrowser() {
        if (!::host.isInitialized) return
        scope.cancel()
        host.close()
        // CEF helper processes stay until the JVM exits (checked by hand: none remain afterwards); only a hang is caught here.
        JcefRuntime.shutdown()
    }

    @AfterEach
    fun closeExtraHosts() {
        extraHosts.forEach { it.close() }
        extraHosts.clear()
    }

    private fun openHost(url: String = "about:blank"): JcefPageHost =
        (JcefPageHost.open(dir, url) as JcefPageHost).also { extraHosts += it }

    private fun htmlFile(name: String, body: String) =
        Files.writeString(Files.createTempDirectory(dir, "page").resolve("$name.html"), body).toUri().toString()

    private fun titleOf(target: WebPageHost, wanted: String): Boolean {
        val end = System.currentTimeMillis() + timeoutMillis
        while (target.page.value.title != wanted && System.currentTimeMillis() < end) Thread.sleep(20)
        return target.page.value.title == wanted
    }

    private fun await(what: String, millis: Long = timeoutMillis, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + millis
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertThat(condition()).describedAs("timeout waiting for $what").isTrue()
    }

    private fun inspectorMessage(prefix: String, waitMillis: Long = timeoutMillis): String? {
        val end = System.currentTimeMillis() + waitMillis
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.startsWith(prefix)) return event.json
        }
        return null
    }

    private fun pixel(bitmap: ImageBitmap, x: Int, y: Int) = IntArray(1).also { bitmap.readPixels(it, x, y, 1, 1) }[0]

    @Test
    fun paints_the_page_into_frames() {
        await("red square painted") { host.frame.value?.let { pixel(it.bitmap, 25, 225) } == 0xFFFF0000.toInt() }
        val frame = host.frame.value!!

        assertThat(frame.widthPx).isEqualTo(640)
        assertThat(pixel(frame.bitmap, 75, 225)).isEqualTo(0xFF00FF00.toInt())
        assertThat(pixel(frame.bitmap, 125, 225)).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun reports_console_output_with_its_level() {
        host.runScript("console.warn('careful')")

        val end = System.currentTimeMillis() + timeoutMillis
        var found: PageEvent.Console? = null
        while (found == null && System.currentTimeMillis() < end) {
            found = events.poll(100, TimeUnit.MILLISECONDS) as? PageEvent.Console
            if (found != null && found.text != "careful") found = null
        }
        assertThat(found?.level).isEqualTo(ConsoleLevel.WARN)
    }

    @Test
    fun injects_the_inspector_on_load_and_forwards_its_messages() {
        host.runScript("cefQuery({request: 'ccm=' + typeof window.__ccm})")

        assertThat(inspectorMessage("ccm=")).isEqualTo("ccm=object")
    }

    @Test
    fun typed_keys_reach_the_input() {
        events.clear()
        host.mouse(MouseEvent.MOUSE_MOVED, 50, 25, MouseEvent.NOBUTTON, 0, 0)
        host.mouse(MouseEvent.MOUSE_PRESSED, 50, 25, MouseEvent.BUTTON1, 1, InputEvent.BUTTON1_DOWN_MASK)
        host.mouse(MouseEvent.MOUSE_RELEASED, 50, 25, MouseEvent.BUTTON1, 1, 0)
        for ((char, vk) in listOf('a' to KeyEvent.VK_A, 'b' to KeyEvent.VK_B)) {
            host.key(keyEvent(KeyEvent.KEY_PRESSED, vk, char))
            host.key(keyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, char))
            host.key(keyEvent(KeyEvent.KEY_RELEASED, vk, char))
        }

        var last: String? = null
        val end = System.currentTimeMillis() + timeoutMillis
        while (last != "value=ab" && System.currentTimeMillis() < end) last = inspectorMessage("value=") ?: last
        assertThat(last).isEqualTo("value=ab")
    }

    @Test
    fun scrolls_by_exact_page_pixels() {
        host.runScript("window.scrollTo(0, 0)")
        events.clear()

        host.scrollBy(300, 300, 200)

        // The page scrolls smoothly, so the first scroll event is only partway.
        var settled = inspectorMessage("scrollY=")
        while (true) settled = inspectorMessage("scrollY=", SETTLE_MILLIS) ?: break
        assertThat(settled).isEqualTo("scrollY=200")
    }

    @Test
    fun captures_a_cropped_png() {
        val png = host.capture(0, 200, 150, 50)!!

        val image = Image.makeFromEncoded(png)
        assertThat(image.width).isEqualTo(150)
        assertThat(image.height).isEqualTo(50)
    }

    private val shotLimits = ShotLimits(32_767, 64_000_000, 52_428_800)

    private fun shotOf(png: ByteArray) = Image.makeFromEncoded(png).let { image ->
        org.jetbrains.skia.Bitmap.makeFromImage(image).also { image.close() }
    }

    private fun overlayAwareHost(url: String, density: Float): JcefPageHost = openHost(url).also {
        it.resize(640, 480, density)
        await("page painted") { it.frame.value != null && !it.page.value.loading && it.page.value.url == url }
        it.runScript("var d=document.createElement('div');d.id='__ccm-root';d.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;background:#f0f';document.body.appendChild(d)")
        Thread.sleep(300)
    }

    @Test
    fun a_whole_page_shot_has_the_page_height_the_bottom_colour_and_no_overlay() {
        val url = htmlFile("tall", "<html><body style=\"margin:0;height:4000px;background:#fff\"><div style=\"position:absolute;left:0;top:3900px;width:100%;height:100px;background:#00f\"></div><div style=\"position:absolute;left:0;top:100px;width:100%;height:50px;background:#f00\"></div><div style=\"position:absolute;left:0;top:1000px;width:100%;height:50px;background:#0a0\"></div></body></html>")
        val tall = overlayAwareHost(url, 1f)
        tall.runScript("window.scrollTo(0, 500)")
        Thread.sleep(300)

        val shot = tall.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.heightPx).isEqualTo(4000)
        assertThat(shot.widthPx).describedAs("viewport minus the 15px scrollbar").isEqualTo(625)
        assertThat(shot.cut).isFalse()
        val bitmap = shotOf(shot.bytes)
        assertThat(bitmap.getColor(320, 3950)).isEqualTo(0xFF0000FF.toInt())
        assertThat(bitmap.getColor(320, 125)).isEqualTo(0xFFFF0000.toInt())
        assertThat(bitmap.getColor(320, 1025)).isEqualTo(0xFF00AA00.toInt())
        assertThat(bitmap.getColor(320, 300)).isEqualTo(0xFFFFFFFF.toInt())
        val restored = LinkedBlockingQueue<PageEvent>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { tall.events.collect { restored.add(it) } }
        tall.runScript("cefQuery({request:'state=' + getComputedStyle(document.getElementById('__ccm-root')).display + '/' + Math.round(window.scrollY)})")
        val state = generateSequence { restored.poll(timeoutMillis, TimeUnit.MILLISECONDS) }
            .firstOrNull { it is PageEvent.Inspector && it.json.startsWith("state=") } as PageEvent.Inspector
        assertThat(state.json).isEqualTo("state=block/500")
    }

    @Test
    fun a_whole_page_shot_of_vh_sections_is_as_tall_as_the_page_at_every_density() {
        val sections = listOf("#f00", "#0a0", "#00f").joinToString("") { "<section style=\"min-height:100vh;background:$it\"></section>" }
        val url = htmlFile("vh", "<html><body style=\"margin:0\">$sections</body></html>")
        for (density in listOf(1f, 2f, 1.5f)) {
            val page = overlayAwareHost(url, density)

            val shot = page.screenshot(ShotArea.WholePage, shotLimits)!!

            assertThat(shot.cssHeight).describedAs("density $density: 3 x the 480px viewport").isEqualTo(1440.0)
            assertThat(shot.heightPx).describedAs("density $density").isEqualTo(Math.round(1440 * density))
            val bitmap = shotOf(shot.bytes)
            assertThat(bitmap.getColor(10, (1400 * density).toInt())).describedAs("density $density: last section").isEqualTo(0xFF0000FF.toInt())
        }
    }

    private fun valueOf(page: JcefPageHost, expression: String): String {
        val seen = LinkedBlockingQueue<PageEvent>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { page.events.collect { seen.add(it) } }
        page.runScript("cefQuery({request:'val=' + ($expression)})")
        val event = generateSequence { seen.poll(timeoutMillis, TimeUnit.MILLISECONDS) }
            .firstOrNull { it is PageEvent.Inspector && it.json.startsWith("val=") } as PageEvent.Inspector
        return event.json.removePrefix("val=")
    }

    @Test
    fun a_whole_page_shot_shows_a_fixed_header_once_and_a_bottom_bar_in_the_last_slice() {
        val url = htmlFile(
            "fixed",
            "<html><body style=\"margin:0;height:3000px;background:#fff\"><div id=\"h\" style=\"position:fixed;left:0;top:0;width:100%;height:60px;background:#f00\"></div>" +
                "<div style=\"position:fixed;left:0;bottom:0;width:100%;height:60px;background:#0a0\"></div></body></html>",
        )
        val page = overlayAwareHost(url, 1f)

        val shot = page.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.heightPx).isEqualTo(3000)
        val bitmap = shotOf(shot.bytes)
        assertThat(bitmap.getColor(300, 30)).describedAs("header in the first slice").isEqualTo(0xFFFF0000.toInt())
        for (y in listOf(510, 990, 1470, 1950)) assertThat(bitmap.getColor(300, y)).describedAs("no header at $y").isEqualTo(0xFFFFFFFF.toInt())
        assertThat(bitmap.getColor(300, 2970)).describedAs("bottom bar at the end").isEqualTo(0xFF00AA00.toInt())
        assertThat(bitmap.getColor(300, 470)).describedAs("no bottom bar in the first slice").isEqualTo(0xFFFFFFFF.toInt())
        assertThat(valueOf(page, "getComputedStyle(document.getElementById('h')).visibility + '/' + document.getElementById('h').style.visibility"))
            .describedAs("header visibility restored").isEqualTo("visible/")
    }

    @Test
    fun an_spa_container_that_hides_horizontal_overflow_is_shot_at_its_visible_width() {
        val url = htmlFile(
            "spa-wide",
            "<html><body style=\"margin:0;height:100%;overflow:hidden\"><div id=\"app\" style=\"position:absolute;inset:0;overflow-x:hidden;overflow-y:auto\"><div style=\"width:3000px;height:1600px;background:#0a0\"></div></div></body></html>",
        )
        val page = overlayAwareHost(url, 1f)
        val visibleWidth = valueOf(page, "document.getElementById('app').clientWidth").toInt()

        val shot = page.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.widthPx).isEqualTo(visibleWidth)
        assertThat(shot.heightPx).isEqualTo(1600)
    }

    @Test
    fun a_plain_page_has_no_horizontal_overflow_with_the_inspector_active_and_after_a_resize() {
        val url = htmlFile("plain", "<html><body style=\"margin:0\"><div style=\"height:2000px;background:#eee\"></div></body></html>")
        val page = openHost(url)
        page.resize(640, 480, 1f)
        await("page painted") { page.frame.value != null && !page.page.value.loading && page.page.value.url == url }
        Thread.sleep(300)
        val overflow = "document.documentElement.scrollWidth - document.documentElement.clientWidth"

        assertThat(valueOf(page, "window.__ccm ? 1 : 0")).describedAs("inspector injected").isEqualTo("1")
        assertThat(valueOf(page, overflow)).isEqualTo("0")
        for ((w, h, density) in listOf(Triple(803, 601, 1.25f), Triple(1153, 700, 1f), Triple(517, 333, 2f))) {
            page.resize(w, h, density)
            Thread.sleep(400)
            assertThat(valueOf(page, overflow)).describedAs("$w x $h at $density").isEqualTo("0")
        }
    }

    @Test
    fun a_whole_page_shot_of_a_document_that_clips_a_wide_glow_keeps_the_visible_width_and_scroll() {
        val url = htmlFile(
            "glow",
            "<html style=\"overflow-x:hidden\"><body style=\"margin:0;height:900px;background:#fff\"><div style=\"position:absolute;left:300px;top:100px;width:2000px;height:50px;background:#f0f\"></div></body></html>",
        )
        val page = overlayAwareHost(url, 1f)
        val visibleWidth = valueOf(page, "document.documentElement.clientWidth").toInt()

        val shot = page.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.widthPx).isEqualTo(visibleWidth)
        assertThat(valueOf(page, "window.scrollX")).isEqualTo("0")
    }

    @Test
    fun a_whole_page_shot_of_a_wide_page_is_stitched_in_columns_without_a_blank_strip() {
        val url = htmlFile("wide", "<html><body style=\"margin:0;width:2000px;height:300px;background:#fff\"><div style=\"position:absolute;left:50px;top:100px;width:50px;height:50px;background:#f00\"></div><div style=\"position:absolute;left:1000px;top:100px;width:50px;height:50px;background:#0a0\"></div><div style=\"position:absolute;left:1900px;top:100px;width:50px;height:50px;background:#00f\"></div></body></html>")
        val wide = overlayAwareHost(url, 1f)
        wide.runScript("window.scrollTo(300, 0)")
        Thread.sleep(500)
        assertThat(wide.scrollOffset()).isEqualTo(PageScroll(300.0, 0.0))

        val shot = wide.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.widthPx).isEqualTo(2000)
        assertThat(shot.heightPx).describedAs("viewport minus the 15px horizontal scrollbar").isEqualTo(465)
        val bitmap = shotOf(shot.bytes)
        assertThat(bitmap.getColor(75, 125)).isEqualTo(0xFFFF0000.toInt())
        assertThat(bitmap.getColor(1025, 125)).isEqualTo(0xFF00AA00.toInt())
        assertThat(bitmap.getColor(1925, 125)).isEqualTo(0xFF0000FF.toInt())
        assertThat(bitmap.getColor(1500, 125)).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(wide.scrollOffset()).describedAs("scroll restored").isEqualTo(PageScroll(300.0, 0.0))
    }

    @Test
    fun a_whole_page_shot_of_an_spa_stitches_the_inner_scroll_container_and_restores_its_scroll() {
        val bands = listOf("#f00", "#0a0", "#00f", "#ff0").joinToString("") { "<div style=\"height:400px;background:$it\"></div>" }
        val url = htmlFile("spa", "<html><body style=\"margin:0;height:100%;overflow:hidden\"><div style=\"position:absolute;left:0;top:0;width:100%;height:40px;background:#000\"></div><div id=\"app\" style=\"position:absolute;left:0;top:40px;right:0;bottom:0;overflow:auto\">$bands</div></body></html>")
        val spa = overlayAwareHost(url, 1f)
        spa.runScript("document.getElementById('app').scrollTop = 300")
        Thread.sleep(300)

        val shot = spa.screenshot(ShotArea.WholePage, shotLimits)!!

        assertThat(shot.heightPx).describedAs("the container's full content").isEqualTo(1600)
        assertThat(shot.cut).isFalse()
        val bitmap = shotOf(shot.bytes)
        assertThat(bitmap.getColor(300, 200)).isEqualTo(0xFFFF0000.toInt())
        assertThat(bitmap.getColor(300, 600)).isEqualTo(0xFF00AA00.toInt())
        assertThat(bitmap.getColor(300, 1000)).isEqualTo(0xFF0000FF.toInt())
        assertThat(bitmap.getColor(300, 1400)).isEqualTo(0xFFFFFF00.toInt())
        val restored = LinkedBlockingQueue<PageEvent>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { spa.events.collect { restored.add(it) } }
        spa.runScript("cefQuery({request:'state=' + document.getElementById('app').scrollTop})")
        val state = generateSequence { restored.poll(timeoutMillis, TimeUnit.MILLISECONDS) }
            .firstOrNull { it is PageEvent.Inspector && it.json.startsWith("state=") } as PageEvent.Inspector
        assertThat(state.json).isEqualTo("state=300")
    }

    @Test
    fun a_shot_is_refused_while_the_page_is_loading() {
        val release = java.util.concurrent.CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/hang.png") release.await(timeoutMillis, TimeUnit.MILLISECONDS)
            val bytes = "<html><body>x<img src=\"/hang.png\"></body></html>".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val loading = openHost("http://127.0.0.1:${server.address.port}/")
            loading.resize(640, 480, 1f)
            await("loading started") { loading.page.value.loading }

            assertThat(loading.screenshot(ShotArea.Visible, shotLimits)).isNull()
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun a_region_shot_of_a_scrolled_page_clips_the_known_block_at_the_device_scale() {
        val url = htmlFile("region", "<html><body style=\"margin:0;height:4000px;background:#fff\"><div style=\"position:absolute;left:100px;top:1100px;width:200px;height:80px;background:#0a0\"></div></body></html>")
        val scrolled = overlayAwareHost(url, 2f)
        scrolled.runScript("window.scrollTo(0, 1000)")
        Thread.sleep(500)

        val shot = scrolled.screenshot(ShotArea.Region(100.0, 100.0, 200.0, 80.0), shotLimits)!!

        assertThat(shot.widthPx).isEqualTo(400)
        assertThat(shot.heightPx).isEqualTo(160)
        val bitmap = shotOf(shot.bytes)
        assertThat(bitmap.getColor(200, 80)).isEqualTo(0xFF00AA00.toInt())
        assertThat(bitmap.getColor(2, 2)).isEqualTo(0xFF00AA00.toInt())
    }

    @Test
    fun a_2560_wide_page_is_shot_without_scaling_and_one_pixel_stripes_stay_exact() {
        val stripes = "repeating-linear-gradient(90deg,#000 0,#000 1px,#fff 1px,#fff 2px)"
        val url = htmlFile("wide2560", "<html><body style=\"margin:0;overflow-x:hidden;height:3000px\"><div style=\"width:2560px;height:3000px;background:$stripes\"></div></body></html>")
        val wide = openHost(url)
        wide.resize(2560, 1080, 1f)
        await("page painted") { wide.frame.value != null && !wide.page.value.loading && wide.page.value.url == url }
        Thread.sleep(300)
        val full = ShotLimits(32_767, 64_000_000, 52_428_800)

        for (area in listOf(ShotArea.Visible, ShotArea.WholePage)) {
            val shot = wide.screenshot(area, full)!!
            val bitmap = shotOf(shot.bytes)

            assertThat(shot.scale).describedAs("$area").isEqualTo(1.0)
            assertThat(shot.widthPx).describedAs("$area width is at least the page width minus the scrollbar").isGreaterThanOrEqualTo(2540)
            val row = (1..400).map { bitmap.getColor(it, 500) and 0xFFFFFF }
            assertThat(row.toSet()).describedAs("$area stripes stay pure black and white").isSubsetOf(0x000000, 0xFFFFFF)
            assertThat(row.zipWithNext().count { it.first != it.second }).describedAs("$area alternates every pixel").isGreaterThan(390)
        }
    }

    @Test
    fun refuses_to_load_script_urls() {
        assertThatThrownBy { host.load("javascript:alert(1)") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun reports_a_failed_load() {
        events.clear()

        host.load(pageUrl.replace("page.html", "missing.html"))

        val failed = generateSequence { events.poll(timeoutMillis, TimeUnit.MILLISECONDS) }.firstOrNull { it is PageEvent.LoadFailed }
        assertThat(failed).isNotNull
        host.load(pageUrl)
        await("reloaded") { host.page.value.url == pageUrl && !host.page.value.loading }
    }

    @Test
    fun a_browser_opened_after_the_only_other_one_was_closed_still_loads() {
        openHost().close()

        val second = openHost()
        second.resize(300, 200, 1f)
        second.load(htmlFile("second", "<html><title>second</title><body>x</body></html>"))

        assertThat(titleOf(second, "second")).isTrue()
    }

    @Test
    fun two_browsers_alive_at_once_both_load() {
        val first = openHost(htmlFile("one", "<html><title>one</title><body>x</body></html>"))
        val second = openHost(htmlFile("two", "<html><title>two</title><body>x</body></html>"))

        assertThat(titleOf(first, "one")).isTrue()
        assertThat(titleOf(second, "two")).isTrue()
        assertThat(first.page.value.title).isEqualTo("one")
    }

    @Test
    fun a_load_issued_immediately_after_open_is_never_dropped() {
        repeat(10) { i ->
            val opened = openHost()
            opened.load(htmlFile("p$i", "<html><title>p$i</title><body>x</body></html>"))
            assertThat(titleOf(opened, "p$i")).describedAs("page $i").isTrue()
            opened.close()
        }
    }

    @Test
    fun open_honors_its_initial_url() {
        val opened = openHost(htmlFile("initial", "<html><title>initial</title><body>x</body></html>"))

        assertThat(titleOf(opened, "initial")).isTrue()
    }

    @Test
    fun an_animation_publishes_a_bounded_number_of_frames() {
        val animated = openHost(htmlFile("anim", ANIMATED))
        animated.resize(400, 300, 1f)
        await("first frame") { animated.frame.value != null }
        val startVersion = animated.frame.value!!.version

        repeat(3) { Thread.sleep(1000); assertThat(animated.openFrameImages).isLessThanOrEqualTo(30) }

        val published = animated.frame.value!!.version - startVersion
        assertThat(published).isBetween(10L, 3_000 / 16 + 30L)
        assertThat(animated.capture(0, 0, 100, 100)).isNotNull
    }

    @Test
    fun a_rotate_repaints_at_the_swapped_size_without_input_and_never_exposes_the_old_size() {
        val rotating = openHost(htmlFile("rotate", ANIMATED))
        rotating.resize(400, 300, 1f)
        await("first frame") { rotating.frame.value?.widthPx == 400 }

        rotating.resize(300, 400, 1f)

        assertThat(rotating.frame.value?.widthPx == 400).isFalse()
        await("frame of the swapped size") { rotating.frame.value?.let { it.widthPx == 300 && it.heightPx == 400 } == true }
    }

    @Test
    fun fractional_and_sub_one_densities_still_publish_frames_that_carry_their_density() {
        listOf(2.625f to 411, 1.5f to 333, 0.5f to 801).forEach { (density, width) ->
            val scaled = openHost(htmlFile("density-$density", ANIMATED))
            scaled.resize(width, 301, density)

            await("frame at density $density") { scaled.frame.value?.density == density.toDouble() }
            assertThat(scaled.capture(0, 0, 50, 50)).describedAs("capture at $density").isNotNull
        }
    }

    @Test
    fun a_hidden_page_publishes_nothing_and_shows_a_fresh_frame_again() {
        val animated = openHost(htmlFile("anim", ANIMATED))
        animated.resize(400, 300, 1f)
        await("first frame") { animated.frame.value != null }

        animated.setHidden(true)
        Thread.sleep(300)
        val frozen = animated.frame.value!!.version
        Thread.sleep(1000)
        assertThat(animated.frame.value!!.version).isEqualTo(frozen)

        animated.setHidden(false)
        await("fresh frame") { animated.frame.value!!.version > frozen }
    }

    @Test
    fun javascript_dialogs_do_not_block_the_page() {
        val dialogs = openHost()
        val seen = LinkedBlockingQueue<PageEvent>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { dialogs.events.collect { seen.add(it) } }
        dialogs.load(htmlFile("dialogs", "<html><body><script>alert('a'); window.confirmed = confirm('b'); prompt('c');</script></body></html>"))
        await("dialog page loaded") { dialogs.page.value.url.endsWith("dialogs.html") && !dialogs.page.value.loading }

        dialogs.runScript("cefQuery({request: 'alive=' + window.confirmed})")

        val reply = generateSequence { seen.poll(timeoutMillis, TimeUnit.MILLISECONDS) }
            .firstOrNull { it is PageEvent.Inspector && it.json.startsWith("alive=") }
        assertThat((reply as PageEvent.Inspector).json).isEqualTo("alive=false")
    }

    @Test
    fun a_console_flood_does_not_push_out_a_later_pick() {
        events.clear()

        host.runScript("for (var i = 0; i < 5000; i++) console.log('line ' + i)")
        host.runScript("cefQuery({request: 'pick-after-flood'})")

        assertThat(inspectorMessage("pick-after-flood")).isEqualTo("pick-after-flood")
    }

    @Test
    fun a_web_page_cannot_navigate_to_local_files() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val html = "<html><title>web</title><body><script>window.open('$pageUrl'); " +
            "setTimeout(function () { location.href = '$pageUrl' }, 200)</script></body></html>"
        server.createContext("/") { exchange ->
            val bytes = html.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val web = openHost("http://127.0.0.1:${server.address.port}/")
            assertThat(titleOf(web, "web")).isTrue()
            Thread.sleep(1500)

            assertThat(web.page.value.url).startsWith("http://127.0.0.1")
        } finally {
            server.stop(0)
        }
    }

    private val awtSource = Canvas()
    private val rawCodeField = KeyEvent::class.java.getDeclaredField("rawCode").apply { isAccessible = true }

    /** Synthetic AWT key events lack the native raw code CEF reads on Windows; real ones from the OS have it. */
    private fun keyEvent(id: Int, vk: Int, char: Char): KeyEvent {
        val event = KeyEvent(awtSource, id, System.currentTimeMillis(), 0, vk, char)
        if (id != KeyEvent.KEY_TYPED) rawCodeField.setLong(event, vk.toLong())
        return event
    }

    private companion object {
        const val SETTLE_MILLIS = 700L
        val ANIMATED = """
            <html><title>anim</title><body style="margin:0"><style>
            @keyframes spin { from { background: #f00 } to { background: #00f } }
            div { width: 400px; height: 300px; animation: spin 0.2s linear infinite alternate }
            </style><div></div></body></html>
        """.trimIndent()
        val PAGE = """
            <html><body style="margin:0;background:#fff;height:3000px">
            <input id=i style="position:absolute;left:10px;top:10px;width:300px;height:30px;font-size:20px" oninput="cefQuery({request:'value='+this.value})">
            <div style="position:fixed;left:0;top:200px;width:50px;height:50px;background:#f00"></div>
            <div style="position:fixed;left:50px;top:200px;width:50px;height:50px;background:#0f0"></div>
            <div style="position:fixed;left:100px;top:200px;width:50px;height:50px;background:#00f"></div>
            <script>window.onscroll=function(){cefQuery({request:'scrollY='+Math.round(window.scrollY)})};</script></body></html>
        """.trimIndent()
    }
}
