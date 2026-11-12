package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.config.AppProperties
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.BrowserFrame
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageState
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebFeedbackWriter
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebReviewProperties
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebScreenshotStore
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.flywaydb.core.Flyway
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import com.github.krpkbhdn.ccmanager.web.StyleChange as StoredStyleChange

private const val PROJECT = 1L
private const val BLOCK_MILLIS = 120_000L
private const val PAGE_URL = "http://localhost:5173/settings"
private const val SETTLE_FRAMES = 20
private const val FRAME_NANOS = 16_000_000L
private const val POLL_MILLIS = 20L
private const val WAIT_MILLIS = 8_000L
private const val SIDE_PANEL_PX = 341
private const val TOOLBAR_PX = 40
private const val DEFAULT_OUT = "ccm-shots/web"

private const val PICK_JSON = """{"type":"pick","target":{
"selector":"main > section.card:nth-child(2) > div.actions > button.btn.btn-primary.save-settings-button",
"xpath":"/html/body/div/main/section[2]/div/button","tag":"button","id":"save","classes":["btn","btn-primary"],"text":"Save changes",
"outerHtml":"<button id=\"save\" class=\"btn btn-primary\">Save changes</button>",
"rect":{"x":560,"y":300,"w":160,"h":40},"viewportRect":{"x":560,"y":300,"w":160,"h":40},
"computed":{"display":"inline-flex","position":"static","width":"160px","height":"40px","margin":"0px 8px 0px 0px","padding":"8px 16px",
"border-width":"1px","color":"rgb(255, 255, 255)","background-color":"rgb(37, 99, 235)","font-family":"Inter, sans-serif",
"font-size":"14px","font-weight":"600","line-height":"20px","opacity":"1"},
"box":{"margin":{"t":0,"r":8,"b":0,"l":0},"border":{"t":1,"r":1,"b":1,"l":1},"padding":{"t":8,"r":16,"b":8,"l":16},"content":{"w":126,"h":22}},
"sourceHint":{"component":"SaveButton","file":"src/components/SaveButton.tsx","line":42}}}"""

private val consoleLines = listOf(
    PageEvent.Console(ConsoleLevel.INFO, "[vite] connected.", "http://localhost:5173/@vite/client", 12),
    PageEvent.Console(ConsoleLevel.WARN, "Each child in a list should have a unique \"key\" prop.", "http://localhost:5173/src/List.tsx", 31),
    PageEvent.Console(ConsoleLevel.ERROR, "Failed to load resource: the server responded with a status of 404 (Not Found)", "http://localhost:5173/api/avatar.png", 0),
    PageEvent.Console(ConsoleLevel.ERROR, "Failed to load resource: the server responded with a status of 404 (Not Found)", "http://localhost:5173/api/avatar.png", 0),
    PageEvent.Console(ConsoleLevel.LOG, "settings loaded {theme: \"dark\"}", "http://localhost:5173/src/main.tsx", 8),
)

private class Rig(val state: WebState, val host: FakePageHost, val view: WebView, val service: WebReviewService)

private class Shot(
    val name: String,
    val unsupported: Boolean = false,
    val failStart: Boolean = false,
    val ownService: Boolean = false,
    val wrap: (WebReviewService) -> WebReviewService = { it },
    val prepare: (Rig) -> Unit = {},
    val click: Pair<Float, Float>? = null,
)

private fun blockingFeedback(real: WebReviewService): WebReviewService = spy(real).also {
    doAnswer { Thread.sleep(BLOCK_MILLIS); null }.`when`(it).prepareFeedback(anyLong())
}

private fun Rig.pendingRevision() = state.revisions(PROJECT).first { it.status() == ReviewStatus.PENDING }

private fun Rig.pickWithComment(text: String) {
    state.setToggles(view, view.toggles.togglePick())
    pick()
    view.comment.setTextAndPlaceCursorAtEnd(text)
}

private val shots = listOf(
    Shot("web-empty-inspector"),
    Shot("web-picked", prepare = { rig ->
        rig.state.setToggles(rig.view, rig.view.toggles.togglePick())
        rig.pick()
        rig.state.editStyle(rig.view, "background-color", "rgb(22, 163, 74)")
        rig.state.editStyle(rig.view, "padding", "12px 24px")
        rig.view.comment.setTextAndPlaceCursorAtEnd("The primary action should use the success colour and be a little roomier.")
    }),
    Shot("web-items", prepare = { rig ->
        rig.view.panelTab = WebPanelTab.ITEMS
        rig.view.selectedItemId = rig.view.pageItems.first().id()
    }),
    Shot("web-editing-item", prepare = { rig -> rig.state.editItem(rig.view, rig.view.pageItems.first()) }),
    Shot("web-console", prepare = { rig ->
        consoleLines.forEach(rig.host::emitEvent)
        rig.view.consoleOpen = true
        waitFor { rig.view.consoleLines.size == 4 }
    }),
    Shot("web-send-dialog", prepare = { rig -> rig.state.send(rig.state.revisions(PROJECT).first()); waitFor { rig.state.sendTarget(PROJECT) != null } }),
    Shot("web-unsupported", unsupported = true),
    Shot("web-draft-other-page", prepare = { rig ->
        rig.pickWithComment("Make this button green.")
        rig.host.page.value = PageState("http://localhost:5173/billing/invoices", "Billing")
        rig.host.emitEvent(PageEvent.Inspector("""{"type":"ready","url":"http://localhost:5173/billing/invoices","title":"Billing"}"""))
        waitFor { rig.view.pageKey?.endsWith("/billing/invoices") == true }
    }),
    Shot("web-next-revision", ownService = true, prepare = { rig ->
        rig.service.prepareFeedback(rig.pendingRevision().id())
        waitFor { rig.state.revisions(PROJECT).none { it.status() == ReviewStatus.PENDING } }
        rig.view.panelTab = WebPanelTab.ITEMS
    }),
    Shot("web-sending", ownService = true, wrap = ::blockingFeedback, prepare = { rig ->
        val pending = rig.pendingRevision()
        rig.service.bindSession(pending.id(), "a1b2")
        waitFor { rig.state.revisions(PROJECT).first().ccmSessionId() == "a1b2" }
        rig.state.send(rig.state.revisions(PROJECT).first())
        waitFor { rig.state.isSending(pending.id()) }
        rig.view.panelTab = WebPanelTab.ITEMS
    }),
    Shot("web-saving", prepare = { rig ->
        rig.pickWithComment("Make this button green.")
        rig.view.draftSaving = true
    }),
    Shot("web-browser-failed", failStart = true),
    Shot("web-spacing-on", prepare = { rig ->
        rig.state.setToggles(rig.view, rig.view.toggles.copy(spacing = setOf(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.VALUES), similar = true))
    }),
    Shot("web-device-4k-50", prepare = { rig ->
        rig.view.device = DeviceSelection.of(DeviceSelection.presets.last()).copy(zoom = DeviceZoom.Percent(50))
    }),
    Shot("web-device-custom", prepare = { rig ->
        rig.view.device = DeviceSelection.custom(2560, 1920).copy(zoom = DeviceZoom.Percent(50))
    }),
    Shot("web-overlay-menu", click = 510f to 19f),
    Shot("web-device-menu", click = 565f to 19f),
    Shot("web-context-menu", prepare = { rig ->
        val layers = listOf(
            LiteTarget("button.save-settings-button", "button", "save", listOf("btn", "btn-primary"), null),
            LiteTarget("div.actions", "div", "", listOf("actions"), null),
            LiteTarget("section.card", "section", "", listOf("card"), null),
        )
        rig.view.contextMenu = ContextMenuState(600.0, 320.0, Offset(600f, 320f), layers)
    }),
    Shot("web-feedback-popover", prepare = { rig ->
        rig.pickWithComment("Make this button green.")
        rig.state.setToggles(rig.view, rig.view.toggles.copy(pick = false))
        rig.view.contextMenu = ContextMenuState(560.0, 300.0, Offset(560f, 300f), emptyList(), feedback = true)
    }),
    Shot("web-item-popover", prepare = { rig ->
        val item = rig.view.pageItems.first { it.n() == 4 }
        rig.view.itemPopover = ItemPopoverState(item.id().toString(), Rect(560.0, 300.0, 160.0, 40.0))
    }),
)

private fun waitFor(timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
}

private fun Rig.pick() {
    host.emitEvent(PageEvent.Inspector(PICK_JSON.replace("\n", "")))
    waitFor { view.picked != null }
}

/** Renders the Web review screen offscreen over a painted fake page. `--out=<dir> --dark|--light --size=WxH`. */
fun main(args: Array<String>) {
    val out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: DEFAULT_OUT)
    val themes = when { "--dark" in args -> listOf(true); "--light" in args -> listOf(false); else -> listOf(true, false) }
    val (w, h) = args.firstOrNull { it.startsWith("--size=") }?.removePrefix("--size=")?.split('x')?.map { it.toInt() } ?: listOf(1280, 820)
    Files.createDirectories(out)
    val service = seededService()
    shots.forEach { shot -> themes.forEach { dark -> render(shot, service, dark, w, h, out) } }
    kotlin.system.exitProcess(0)
}

/** A real service on a throwaway SQLite: a sent revision of two items (one dismissed) and a pending one with two more. */
private fun seededService(): WebReviewService {
    val dir = Files.createTempDirectory("ccm-web-shots")
    val dataSource = DriverManagerDataSource("jdbc:sqlite:${dir.resolve("shots.db")}?journal_mode=WAL&busy_timeout=5000&foreign_keys=on")
    Flyway.configure().dataSource(dataSource).load().migrate()
    val jdbc = JdbcClient.create(dataSource)
    jdbc.sql("INSERT INTO project (id, name, path, created_at) VALUES (?, 'cc-manager', 'preview', 'now')").param(PROJECT).update()
    val properties = WebReviewProperties(600, listOf("display"), 1024, 50)
    val service = WebReviewService(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)), properties, WebScreenshotStore(AppProperties(dir), properties), WebFeedbackWriter(properties))
    fun add(selector: String, tag: String, rect: ElementAnchor.Rect, comment: String, vararg changes: StoredStyleChange) =
        service.addItem(PROJECT, PAGE_URL, "Settings", ElementAnchor(selector, "/html/body/$tag", tag, ElementAnchor.SourceHint("Settings", "src/Settings.tsx", 20), rect, "", "", "<$tag>", mapOf("color" to "red")), comment, changes.toList())
    val first = add("section.card:nth-child(1) > h2.title", "h2", ElementAnchor.Rect(260.0, 150.0, 300.0, 30.0), "Heading is too light.", StoredStyleChange("font-weight", "500", "700"))
    add("header > nav a.logo", "a", ElementAnchor.Rect(40.0, 18.0, 120.0, 30.0), "Logo is not vertically centred.")
    val dismissed = add("footer > p.copyright", "p", ElementAnchor.Rect(260.0, 600.0, 300.0, 20.0), "Typo in the footer.")
    service.setStatus(dismissed.id(), WebItemStatus.DISMISSED)
    service.prepareFeedback(first.revisionId())
    add("main > section.card:nth-child(2) > div.actions > button.save-settings-button", "button", ElementAnchor.Rect(560.0, 300.0, 160.0, 40.0), "Primary button should be green.", StoredStyleChange("background-color", "rgb(37, 99, 235)", "rgb(22, 163, 74)"))
    add("aside.help > p", "p", ElementAnchor.Rect(780.0, 200.0, 200.0, 60.0), "Help text overflows its card.")
    return service
}

private fun session(id: String) = mock(SessionSnapshot::class.java).also {
    `when`(it.ccmSessionId).thenReturn(id)
    `when`(it.projectId).thenReturn(PROJECT)
    `when`(it.title).thenReturn("Fix settings page ($id)")
}

private fun render(shot: Shot, service: WebReviewService, dark: Boolean, width: Int, height: Int, out: Path) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val errors = mutableListOf<String>()
    val host = FakePageHost()
    val availability = if (shot.unsupported) Availability.Unsupported("This Java runtime has no embedded browser (JetBrains Runtime with JCEF is required)") else Availability.Ready
    val shotService = shot.wrap(if (shot.ownService) seededService() else service)
    val prepareBrowser: (() -> Unit)? = if (shot.failStart) ({ error("JCEF did not initialize within 30 seconds") }) else null
    val state = WebState(shotService, { host }, { _, _ -> }, { _, _, _, _ -> }, { LiveSessions(sessions = listOf(session("a1b2"), session("c3d4"))) }, scope, { errors += it }, { availability }, prepareBrowser)
    state.prepare()
    waitFor { state.browserReady || state.unsupportedReason() != null || state.browserFailure != null }
    val view = if (shot.unsupported || shot.failStart) null else state.view(PROJECT)
    if (view != null) {
        host.frame.value = pageFrame(width - SIDE_PANEL_PX, height - TOOLBAR_PX)
        host.page.value = PageState(PAGE_URL, "Settings", canGoBack = true)
        host.emitEvent(PageEvent.Inspector("""{"type":"ready","url":"$PAGE_URL","title":"Settings"}"""))
        waitFor { view.pageItems.size == 4 && state.revisions(PROJECT).size == 2 && state.items(state.revisions(PROJECT).first().id()).isNotEmpty() }
        shot.prepare(Rig(state, host, view, shotService))
    }
    val scene = ImageComposeScene(width, height, Density(1f)) { AppTheme(dark) { WebScreen(PROJECT, state, Modifier.background(colors.background)) } }
    try {
        var frame = 0L
        repeat(SETTLE_FRAMES) { scene.render(frame++ * FRAME_NANOS) }
        shot.click?.let { (x, y) ->
            val at = Offset(x, y)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
        }
        waitFor(300) { false }
        repeat(SETTLE_FRAMES) { scene.render(frame++ * FRAME_NANOS) }
        val file = out.resolve("${shot.name}-${if (dark) "dark" else "light"}-${width}x$height.png")
        Files.write(file, scene.render(frame * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("wrote $file${if (errors.isEmpty()) "" else " errors=$errors"}")
    } finally {
        state.close()
        scope.cancel()
        runCatching { scene.close() }
    }
}

private fun fill(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, argb: Long, radius: Float = 0f) =
    canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), Paint().apply { color = argb.toInt() })

/** A colourful stand-in for a settings page: header, sidebar, two cards with a primary button and a help box, plus the numbered pins. */
private fun pageFrame(width: Int, height: Int): BrowserFrame {
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    fill(canvas, 0f, 0f, width.toFloat(), height.toFloat(), 0xFFF1F5F9)
    fill(canvas, 0f, 0f, width.toFloat(), 64f, 0xFF0F172A)
    fill(canvas, 40f, 18f, 120f, 30f, 0xFF38BDF8, 6f)
    fill(canvas, 0f, 64f, 200f, (height - 64).toFloat(), 0xFFE2E8F0)
    listOf(0xFFF59E0B, 0xFFEF4444, 0xFF8B5CF6, 0xFF10B981).forEachIndexed { i, c -> fill(canvas, 24f, 100f + i * 48f, 150f, 32f, c, 8f) }
    fill(canvas, 260f, 140f, 480f, 120f, 0xFFFFFFFF, 12f)
    fill(canvas, 260f, 150f, 300f, 30f, 0xFF94A3B8, 4f)
    fill(canvas, 260f, 270f, 480f, 200f, 0xFFFFFFFF, 12f)
    fill(canvas, 280f, 290f, 440f, 24f, 0xFFCBD5E1, 4f)
    fill(canvas, 560f, 300f, 160f, 40f, 0xFF2563EB, 8f)
    fill(canvas, 780f, 200f, 200f, 60f, 0xFFFDE68A, 10f)
    fill(canvas, 260f, 600f, 300f, 20f, 0xFFCBD5E1, 4f)
    val pins = listOf(1 to (260f to 150f), 2 to (40f to 18f), 4 to (560f to 300f), 5 to (780f to 200f))
    val font = Font().apply { size = 12f }
    pins.forEach { (n, at) ->
        fill(canvas, at.first - 9f, at.second - 9f, 18f, 18f, 0xFFF97316, 9f)
        canvas.drawString(n.toString(), at.first - 3.5f, at.second + 4f, font, Paint().apply { color = 0xFFFFFFFF.toInt() })
    }
    return BrowserFrame(surface.makeImageSnapshot().toComposeImageBitmap(), width, height, 1)
}
