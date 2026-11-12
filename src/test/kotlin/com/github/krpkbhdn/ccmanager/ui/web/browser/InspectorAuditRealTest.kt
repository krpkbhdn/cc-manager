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

/** Runs the layout audit of the injected inspector in the real Chromium: `CCM_REAL_JCEF=1 ./gradlew test --tests '*InspectorAuditRealTest'`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InspectorAuditRealTest {
    private val timeoutMillis = 10_000L
    private lateinit var host: WebPageHost
    private lateinit var scope: CoroutineScope
    private val events = LinkedBlockingQueue<PageEvent>()

    @BeforeAll
    fun startBrowser() {
        assumeTrue(System.getenv("CCM_REAL_JCEF") == "1", "set CCM_REAL_JCEF=1 to run the real browser")
        assertThat(JcefRuntime.availability()).isEqualTo(Availability.Ready)
        // Not a @TempDir: CEF keeps its cache files locked until the JVM exits.
        val dir = Files.createDirectories(Path.of("build/jcef-audit-test"))
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        val html = javaClass.getResource("/web-review/fixtures/audit.html")!!.readText()
        val url = Files.writeString(dir.resolve("audit.html"), html).toUri().toString()
        host = JcefPageHost.open(dir)
        scope = CoroutineScope(Dispatchers.Default)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { host.events.collect { events.add(it) } }
        host.resize(640, 480, 1f)
        host.load(url)
        val end = System.currentTimeMillis() + timeoutMillis
        while ((host.page.value.url != url || host.page.value.loading) && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(500)
    }

    @AfterAll
    fun stopBrowser() {
        if (!::host.isInitialized) return
        scope.cancel()
        host.close()
        JcefRuntime.shutdown()
    }

    private fun findings(spacing: String): List<String> {
        events.clear()
        host.runScript(
            "window.__ccm.setOptions({spacing: $spacing});" +
                "cefQuery({request: 'audit=' + window.__ccm.auditDebug().map(function (f) { return f.kind + ' ' + f.selector; }).join('|')})",
        )
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            val event = events.poll(100, TimeUnit.MILLISECONDS)
            if (event is PageEvent.Inspector && event.json.startsWith("audit=")) {
                return event.json.removePrefix("audit=").split('|').filter { it.isNotEmpty() }
            }
        }
        error("no audit answer")
    }

    @Test
    fun flags_the_planted_layout_defects_and_nothing_else() {
        val found = findings("['BORDERS', 'PHANTOMS', 'MISALIGN']")

        assertThat(found).containsExactlyInAnyOrder(
            "BORDER_ODD #card-b",
            "PHANTOM #phantom",
            "PHANTOM #ghost",
            "MISALIGN #mis-b",
            "UNEVEN_GAP #gap-c",
        )
    }

    @Test
    fun reacts_only_to_the_enabled_kinds() {
        assertThat(findings("['PHANTOMS']")).containsExactlyInAnyOrder("PHANTOM #phantom", "PHANTOM #ghost")
        assertThat(findings("['SOMETHING_ELSE']")).isEmpty()
    }
}
