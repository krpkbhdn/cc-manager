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
