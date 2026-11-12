package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.review.Review
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.web.browser.Availability
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageEvent
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageScroll
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageShot
import com.github.krpkbhdn.ccmanager.ui.web.browser.ShotArea
import com.github.krpkbhdn.ccmanager.ui.web.browser.PageState
import com.github.krpkbhdn.ccmanager.web.BrowserLimits
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebFeedback
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebViewport
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebPage
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebRevision
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import com.github.krpkbhdn.ccmanager.web.StyleChange as StoredStyleChange

class WebStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(WebReviewService::class.java)
    private val host = FakePageHost()
    private val reviews = mock(ReviewService::class.java)
    private val opened = CopyOnWriteArrayList<Pair<Long, Long>>()
    private val errors = CopyOnWriteArrayList<String>()
    private val enqueued = CopyOnWriteArrayList<Pair<String, String>>()
    private val started = CopyOnWriteArrayList<String>()
    private var liveSessions = LiveSessions()
    private var availability: Availability = Availability.Ready
    private val state = WebState(service, { host }, { id, text -> enqueued += id to text }, { _, prompt, _, onStarted -> started += prompt; onStarted("new-session") }, { liveSessions }, scope, { errors += it }, reviews, { project, review -> opened += project to review }, { availability })

    init {
        `when`(service.subscribe(any())).thenReturn(Runnable { })
        `when`(service.browserLimits()).thenReturn(BrowserLimits(3, Duration.ofMinutes(10)))
    }

    @AfterEach
    fun stop() {
        state.close()
        scope.cancel()
    }

    private fun anchor() = ElementAnchor("button.save", "/html/body/button", "button", null, ElementAnchor.Rect(1.0, 2.0, 3.0, 4.0), "", "Save", "<button>", mapOf("color" to "red"))

    private fun item(id: Long, status: WebItemStatus = WebItemStatus.OPEN, revisionId: Long = 5) =
        WebItem(id, revisionId, 9, id.toInt(), anchor(), "c$id", emptyList(), status, null, null)

    private fun revision(id: Long, status: ReviewStatus, session: String? = null) = WebRevision(id, 1, id.toInt(), session, null, status, Instant.EPOCH, null, null)

    private fun snapshot(id: String) = mock(SessionSnapshot::class.java).also {
        `when`(it.ccmSessionId).thenReturn(id)
        `when`(it.projectId).thenReturn(1L)
        `when`(it.title).thenReturn("Session $id")
    }

    private fun ready(url: String = "http://localhost:3000/app") {
        host.page.value = PageState(url = url)
        emit("""{"type":"ready","url":"$url","title":"App"}""")
    }

    private fun emit(json: String) = host.emitEvent(PageEvent.Inspector(json))

    private fun pickJson(selector: String = "button.save") =
        """{"type":"pick","target":{"selector":"$selector","tag":"button","computed":{"color":"rgb(0, 0, 0)","padding":"4px"},"viewportRect":{"x":10,"y":20,"w":30,"h":40}}}"""

    private fun openView(): WebView = state.view(1).also { await { host.events.subscriptionCount.value > 0 } }

    private fun picked(view: WebView) {
        ready()
        await { view.pageKey != null }
        state.setToggles(view, view.toggles.togglePick())
        emit(pickJson())
        await { view.picked != null }
        if (host.captureResult != null) await { view.pickShot != null }
    }

    @Test
    fun `an unsupported runtime is reported once without creating a browser`() {
        availability = Availability.Unsupported("no JCEF")

        state.prepare()

        await { state.unsupportedReason() == "no JCEF" }
        assertThat(state.browserReady).isFalse()
    }

    @Test
    fun `a supported runtime becomes ready and a failed start can be retried`() {
        var attempts = 0
        val retrying = WebState(service, { host }, { _, _ -> }, { _, _, _, _ -> }, { liveSessions }, scope, { errors += it }, reviews, { _, _ -> }, { Availability.Ready }, prepareBrowser = {
            if (attempts++ == 0) error("engine down")
        })

        retrying.prepare()
        await { retrying.browserFailure == "engine down" }
        retrying.retry()

        await { retrying.browserReady }
        assertThat(retrying.browserFailure).isNull()
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `opening a project restores the last page that had items`() {
        `when`(service.pages(1)).thenReturn(listOf(WebPage(1, 1, "k", "http://localhost:3000/old", "Old")))

        openView()

        await { host.loads == listOf("http://localhost:3000/old") }
    }

    @Test
    fun `a ready page loads its items and pushes pins and options`() {
        `when`(service.items(1, "http://localhost:3000/app")).thenReturn(listOf(item(3)))
        val view = openView()

        ready()

        await { view.pageItems.size == 1 && host.scripts.any { it.contains("setAnnotations") && it.contains("\"n\":3") } }
        assertThat(host.scripts).anyMatch { it.contains("setOptions") }
        assertThat(view.pageUrl).isEqualTo("http://localhost:3000/app")
    }

    @Test
    fun `the page address comes from the browser, not from the page's own message`() {
        `when`(service.items(1, "http://localhost:3000/app")).thenReturn(listOf(item(3)))
        `when`(service.items(1, "http://localhost:3000/secret")).thenReturn(listOf(item(8)))
        val view = openView()
        host.page.value = PageState(url = "http://localhost:3000/app")

        emit("""{"type":"ready","url":"http://localhost:3000/secret","title":"Fake"}""")

        await { view.pageItems.map { it.id() } == listOf(3L) }
        assertThat(view.pageKey).isEqualTo("http://localhost:3000/app")
        verify(service, never()).items(1, "http://localhost:3000/secret")
    }

    @Test
    fun `picking an element selects it and switches pick mode off`() {
        val view = openView()
        ready()
        await { view.pageKey != null }
        state.setToggles(view, view.toggles.togglePick())
        assertThat(host.scripts).anyMatch { it.contains("setOptions") && it.contains("\"pick\":true") }

        emit(pickJson())

        await { view.picked != null }
        await { !view.toggles.pick }
        assertThat(view.picked!!.selector).isEqualTo("button.save")
        await { host.scripts.any { it.contains("setOptions") && it.contains("\"pick\":false") } }
    }

    @Test
    fun `a pick or hover the user did not ask for is ignored`() {
        val view = openView()
        ready()
        await { view.pageKey != null }

        emit(pickJson())
        emit(pickJson().replace("\"pick\"", "\"hover\""))
        ready()
        await { host.scripts.count { it.contains("setAnnotations") } >= 2 }

        assertThat(view.picked).isNull()
        assertThat(view.hovered).isNull()
    }

    @Test
    fun `a forged pick never replaces a draft the user has typed`() {
        val view = openView()
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("my comment")
        view.toggles = view.toggles.copy(pick = true)

        emit(pickJson("evil.target"))
        ready()
        await { host.scripts.count { it.contains("setAnnotations") } >= 2 }

        assertThat(view.picked!!.selector).isEqualTo("button.save")
        assertThat(view.comment.text.toString()).isEqualTo("my comment")
        state.setToggles(view, view.toggles.copy(pick = false))
        state.setToggles(view, view.toggles.copy(pick = true))
        assertThat(errors).containsExactly("Finish or cancel the current item before picking another element")
    }

    @Test
    fun `typing an address with another scheme reports instead of loading`() {
        val view = openView()

        state.go(view, "ftp://example.com")
        state.go(view, "chrome://version")
        state.go(view, "localhost:3000")

        assertThat(errors).containsExactly("Only http, https and file addresses can be opened", "Only http, https and file addresses can be opened")
        assertThat(host.loads).contains("http://localhost:3000").doesNotContain("ftp://example.com")
    }

    @Test
    fun `a project that is gone loses its browser`() {
        openView()

        state.retain(listOf(2L))

        assertThat(host.closed).isTrue()
    }

    @Test
    fun `a second send while one is running queues the prompt once`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s1")))
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(service.prepareFeedback(5)).thenAnswer { release.await(); WebFeedback(Path.of("web.md"), "Read web.md") }
        val revision = revision(5, ReviewStatus.PENDING, "s1")

        state.send(revision)
        await { state.isSending(5) }
        state.send(revision)
        release.countDown()

        await { enqueued.isNotEmpty() && !state.isSending(5) }
        assertThat(enqueued).hasSize(1)
        verify(service, times(1)).prepareFeedback(5)
    }

    @Test
    fun `malformed inspector messages are ignored`() {
        val view = openView()

        emit("not json")
        emit("""{"type":"pick","target":5}""")
        ready()

        await { view.pageKey != null }
        assertThat(view.picked).isNull()
        assertThat(errors).isEmpty()
    }

    @Test
    fun `editing a style previews only the changed property and clears when reverted`() {
        val view = openView()
        picked(view)

        state.editStyle(view, "color", "blue")
        assertThat(host.scripts.last()).contains("previewStyle").contains("button.save").contains("\"color\":\"blue\"").doesNotContain("padding")

        state.editStyle(view, "color", "rgb(0, 0, 0)")
        assertThat(host.scripts.last()).contains("clearPreview")
    }

    @Test
    fun `adding stores the item with the anchor and changes then saves the screenshot`() {
        val png = byteArrayOf(1, 2, 3)
        host.captureResult = png
        `when`(service.addItem(eq(1L), eq("http://localhost:3000/app"), eq("App"), any(), eq("too tight"), anyList(), any())).thenReturn(item(7))
        val view = openView()
        view.layout = PageViewport(390, 844)
        view.device = DeviceSelection.of(DeviceSelection.presets.first { it.name == "iPhone 14" })
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("too tight")
        state.editStyle(view, "padding", "8px")
        val expectedAnchor = view.picked!!.toAnchor()

        state.saveDraft(view)

        verify(service, timeout(3000)).addItem(1, "http://localhost:3000/app", "App", expectedAnchor, "too tight", listOf("top", "right", "bottom", "left").map { StoredStyleChange("padding-$it", "", "8px") }, WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE))
        verify(service, timeout(3000)).saveScreenshot(7, png)
        await { view.picked == null }
        assertThat(host.scripts.last()).contains("clearPreview")
        assertThat(errors).isEmpty()
    }

    @Test
    fun `the item records the viewport of the pick, not the one at save time`() {
        `when`(service.addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())).thenReturn(item(7))
        val view = openView()
        view.layout = PageViewport(390, 844)
        view.device = DeviceSelection.of(DeviceSelection.presets.first { it.name == "iPhone 14" })
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("x")
        view.layout = PageViewport(1280, 720)
        view.device = DeviceSelection.Responsive

        state.saveDraft(view)

        verify(service, timeout(3000)).addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), eq(WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE)))
    }

    @Test
    fun `a pending jump to an item hidden on this device is dropped`() {
        val hidden = item(12).let { WebItem(it.id(), it.revisionId(), it.pageId(), it.n(), it.anchor(), it.comment(), it.styleChanges(), it.status(), null, WebViewport(1280, 720, null, DeviceCategory.DESKTOP)) }
        `when`(service.items(1, "http://localhost:3000/app")).thenReturn(listOf(hidden))
        val view = openView()
        view.device = DeviceSelection(DeviceSelection.presets.first { it.name == "iPhone 14" }, 390, 844)
        view.pendingFocus = 12
        ready()

        await { view.pageItems.size == 1 }
        await { view.pendingFocus == null }
        assertThat(host.scripts).noneMatch { it.contains("focusAnnotation") }
    }

    @Test
    fun `a failing screenshot never blocks the item`() {
        host.captureResult = byteArrayOf(1)
        `when`(service.addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())).thenReturn(item(7))
        `when`(service.saveScreenshot(7, host.captureResult!!)).thenThrow(IllegalArgumentException("not a png"))
        val view = openView()
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("x")

        state.saveDraft(view)

        await { view.picked == null }
        assertThat(errors).isEmpty()
    }

    @Test
    fun `navigating to another page re-keys the items and drops the draft`() {
        `when`(service.items(1, "http://localhost:3000/other")).thenReturn(listOf(item(4)))
        val view = openView()
        picked(view)

        ready("http://localhost:3000/other?utm_source=x")

        await { view.pageItems.map { it.id() } == listOf(4L) }
        assertThat(view.picked).isNull()
        assertThat(view.pageKey).isEqualTo("http://localhost:3000/other")
    }

    @Test
    fun `a page that cannot be keyed leaves no items and no error`() {
        val view = openView()
        ready()
        await { view.pageKey != null }

        ready("about:blank")

        await { view.pageKey == null }
        assertThat(view.pageItems).isEmpty()
        assertThat(errors).isEmpty()
    }

    @Test
    fun `selecting an item on another page opens that page and focuses the pin once it is ready`() {
        `when`(service.pages(1)).thenReturn(listOf(WebPage(9, 1, "http://localhost:3000/other", "http://localhost:3000/other?x=1", "Other")))
        `when`(service.items(1, "http://localhost:3000/other")).thenReturn(listOf(item(4)))
        val view = openView()
        await { host.loads.isNotEmpty() }
        host.loads.clear()
        state.revisions(1)
        await { state.pages(1).isNotEmpty() }

        state.selectItem(view, item(4))
        assertThat(host.loads).containsExactly("http://localhost:3000/other?x=1")
        ready("http://localhost:3000/other")

        await { host.scripts.any { it.contains("focusAnnotation") && it.contains("\"4\"") } }
    }

    @Test
    fun `the badge counts open items of the pending revision only`() {
        `when`(service.revisions(1)).thenReturn(listOf(revision(6, ReviewStatus.PENDING), revision(5, ReviewStatus.SENT)))
        `when`(service.allItems(6)).thenReturn(listOf(item(1, revisionId = 6), item(2, WebItemStatus.DISMISSED, 6)))
        `when`(service.allItems(5)).thenReturn(listOf(item(3), item(4)))

        state.pendingCount(1)

        await { state.pendingCount(1) == 1 }
    }

    @Test
    fun `sending to the bound live session binds, marks and enqueues the prompt`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s1")))
        `when`(service.prepareFeedback(5)).thenReturn(WebFeedback(Path.of("web.md"), "Read web.md"))

        state.send(revision(5, ReviewStatus.PENDING, "s1"))

        await { enqueued.isNotEmpty() }
        assertThat(enqueued).containsExactly("s1" to "Read web.md")
        verify(service).bindSession(5, "s1")
    }

    @Test
    fun `sending without a session offers the live ones and a pick enqueues`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s2")))
        `when`(service.prepareFeedback(5)).thenReturn(WebFeedback(Path.of("web.md"), "Read web.md"))
        val revision = revision(5, ReviewStatus.PENDING)

        state.send(revision)
        await { state.sendTarget(1) != null }
        assertThat(state.sendTarget(1)!!.candidates.map { it.ccmSessionId }).containsExactly("s2")
        verify(service, never()).prepareFeedback(5)

        state.sendTo(revision, "s2")

        await { enqueued.isNotEmpty() }
        assertThat(state.sendTarget(1)).isNull()
    }

    @Test
    fun `sending to a new session starts one and binds it`() {
        `when`(service.prepareFeedback(5)).thenReturn(WebFeedback(Path.of("web.md"), "Read web.md"))

        state.sendTo(revision(5, ReviewStatus.PENDING), null)

        await { started.isNotEmpty() }
        assertThat(started).containsExactly("Read web.md")
        verify(service, timeout(3000)).bindSession(5, "new-session")
    }

    @Test
    fun `a dead bound session reopens the chooser and marks nothing sent`() {
        state.send(revision(5, ReviewStatus.PENDING, "gone"))

        await { state.sendTarget(1) != null }
        verify(service, never()).prepareFeedback(5)
        assertThat(enqueued).isEmpty()
    }

    @Test
    fun `approving and starting the next revision go to the service`() {
        state.approve(revision(5, ReviewStatus.SENT))
        state.startNextRevision(1)

        verify(service, timeout(3000)).approve(5)
        verify(service, timeout(3000)).startNextRevision(1)
    }

    @Test
    fun `closing disposes every browser`() {
        openView()

        state.close()

        assertThat(host.closed).isTrue()
    }

    private fun preparing(start: () -> Unit) = WebState(service, { host }, { _, _ -> }, { _, _, _, _ -> }, { liveSessions }, scope, { errors += it }, reviews, { _, _ -> }, prepareBrowser = start)

    @Test
    fun `the browser becomes ready after the engine started off the caller thread`() {
        val caller = Thread.currentThread()
        var startedOn: Thread? = null
        val preparing = preparing { startedOn = Thread.currentThread() }
        assertThat(preparing.browserReady).isFalse()

        preparing.prepare()

        await { preparing.browserReady }
        assertThat(startedOn).isNotNull().isNotSameAs(caller)
        assertThat(preparing.browserFailure).isNull()
    }

    @Test
    fun `an engine that cannot start is reported as a failure instead of ready`() {
        val preparing = preparing { throw IllegalStateException("JCEF did not initialize") }

        preparing.prepare()

        await { preparing.browserFailure != null }
        assertThat(preparing.browserFailure).isEqualTo("JCEF did not initialize")
        assertThat(preparing.browserReady).isFalse()
    }

    @Test
    fun `a double click on Add creates one item`() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(service.addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())).thenAnswer { release.await(); item(7) }
        val view = openView()
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("once")

        state.saveDraft(view)
        state.saveDraft(view)
        release.countDown()

        await { !view.draftSaving }
        verify(service, times(1)).addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())
    }

    @Test
    fun `a failed Add puts the draft back and reports`() {
        `when`(service.addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())).thenThrow(IllegalArgumentException("revision full"))
        val view = openView()
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("keep me")

        state.saveDraft(view)

        await { errors.isNotEmpty() && !view.draftSaving }
        assertThat(errors).containsExactly("revision full")
        assertThat(view.picked!!.selector).isEqualTo("button.save")
        assertThat(view.comment.text.toString()).isEqualTo("keep me")
    }

    @Test
    fun `a typed draft survives navigation and is saved against the page it was picked on`() {
        `when`(service.addItem(anyLong(), anyString(), anyString(), any(), anyString(), anyList(), any())).thenReturn(item(7))
        val view = openView()
        picked(view)
        view.comment.setTextAndPlaceCursorAtEnd("for the first page")

        ready("http://localhost:3000/other")
        await { view.pageKey == "http://localhost:3000/other" }

        assertThat(view.picked).isNotNull()
        assertThat(view.draftPageKey).isEqualTo("http://localhost:3000/app")
        state.saveDraft(view)
        verify(service, timeout(3000)).addItem(eq(1L), eq("http://localhost:3000/app"), anyString(), any(), eq("for the first page"), anyList(), any())
    }

    @Test
    fun `an untyped pick is dropped when the page changes`() {
        val view = openView()
        picked(view)

        ready("http://localhost:3000/other")

        await { view.picked == null }
    }

    @Test
    fun `every real page change is recorded as the last visited page`() {
        val view = openView()

        ready()
        await { view.pageKey != null }
        ready("http://localhost:3000/other")

        verify(service, timeout(3000)).touchPage(1, "http://localhost:3000/app", "App")
        verify(service, timeout(3000)).touchPage(1, "http://localhost:3000/other", "App")
    }

    @Test
    fun `pins of a page are not pushed once the browser moved on`() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val returned = java.util.concurrent.atomic.AtomicBoolean()
        `when`(service.items(1, "http://localhost:3000/app")).thenAnswer { gate.await(); returned.set(true); listOf(item(3)) }
        val view = openView()
        ready()
        await { view.pageKey == "http://localhost:3000/app" }

        host.page.value = PageState(url = "http://localhost:3000/other")
        gate.countDown()
        await { returned.get() }
        ready("http://localhost:3000/other")
        await { view.pageKey == "http://localhost:3000/other" }

        await { host.scripts.any { it.contains("setAnnotations") } }
        assertThat(host.scripts).noneMatch { it.contains("setAnnotations") && it.contains("\"n\":3") }
    }

    @Test
    fun `pins pushed to the page obey the device scope and follow its change`() {
        fun viewItem(id: Long, viewport: WebViewport?) = item(id).let {
            WebItem(it.id(), it.revisionId(), it.pageId(), it.n(), it.anchor(), it.comment(), it.styleChanges(), it.status(), null, viewport)
        }
        `when`(service.items(1, "http://localhost:3000/app")).thenReturn(
            listOf(
                viewItem(11, WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE)),
                viewItem(12, WebViewport(820, 1180, "iPad Air", DeviceCategory.TABLET)),
                viewItem(13, null),
            ),
        )
        val view = openView()
        ready()
        await { view.pageItems.size == 3 && host.scripts.any { it.contains("setAnnotations") } }

        host.scripts.clear()
        view.device = DeviceSelection(DeviceSelection.presets.first { it.name == "iPhone 14" }, 390, 844)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()

        await { host.scripts.any { it.contains("setAnnotations") } }
        val pushed = host.scripts.last { it.contains("setAnnotations") }
        assertThat(pushed).contains("\"n\":11").contains("\"n\":13").doesNotContain("\"n\":12")
        assertThat(view.visibleItems.map { it.n() }).containsExactly(11, 13)

        host.scripts.clear()
        view.device = DeviceSelection.Responsive
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        await { host.scripts.any { it.contains("setAnnotations") } }
        assertThat(host.scripts.last { it.contains("setAnnotations") }).contains("\"n\":11", "\"n\":12", "\"n\":13")
    }

    @Test
    fun `the chooser of one project is not shown for another`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s2")))

        state.send(revision(5, ReviewStatus.PENDING))

        await { state.sendTarget(1) != null }
        assertThat(state.sendTarget(2)).isNull()
    }

    @Test
    fun `lock and editing flags follow actions and messages and editing resets on navigation`() {
        val view = openView()
        ready()
        await { view.pageKey != null }

        state.perform(view, WebAction.ToggleLock)
        emit("""{"type":"editing","on":true}""")

        await { view.pageEditing }
        assertThat(view.interaction).isFalse()
        ready("http://localhost:3000/other")
        await { !view.pageEditing }
        state.perform(view, WebAction.ToggleLock)
        assertThat(view.interaction).isTrue()
    }

    @Test
    fun `sticky pick turns picking on and Esc leaves it`() {
        val view = openView()
        ready()
        await { view.pageKey != null }

        state.perform(view, WebAction.StickyPick)
        assertThat(view.stickyPick).isTrue()
        assertThat(view.toggles.pick).isTrue()
        state.perform(view, WebAction.StopPick)
        assertThat(view.stickyPick).isFalse()
        assertThat(view.toggles.pick).isFalse()
    }

    @Test
    fun `a sticky pick keeps picking on and opens the feedback popover at the pointer, a dirty draft refuses the next pick`() {
        val view = openView()
        ready()
        await { view.pageKey != null }
        view.lastPointer = PointerSpot(Offset(10f, 20f), Offset(111f, 222f))
        state.perform(view, WebAction.StickyPick)

        emit(pickJson())

        await { view.picked != null && view.contextMenu != null }
        assertThat(view.toggles.pick).isTrue()
        assertThat(view.stickyPick).isTrue()
        assertThat(view.contextMenu!!.feedback).isTrue()
        assertThat(view.contextMenu!!.canvasOffset).isEqualTo(Offset(111f, 222f))
        view.comment.setTextAndPlaceCursorAtEnd("typed")
        emit(pickJson("other.target"))
        await { errors.isNotEmpty() }
        assertThat(view.picked!!.selector).isEqualTo("button.save")
    }

    @Test
    fun `quick feedback needs a pointer position`() {
        val view = openView()
        ready()
        await { view.pageKey != null }

        state.perform(view, WebAction.QuickFeedback)

        assertThat(errors).containsExactly(NO_POINTER_NOTICE)
    }

    @Test
    fun `Esc on a quick feedback menu drops the pending inspect so a late answer starts no draft`() {
        val view = openView()
        ready()
        await { view.pageKey != null }
        state.requestInspect(view, "button.save", REASON_FEEDBACK)
        view.contextMenu = ContextMenuState(0.0, 0.0, Offset.Zero, emptyList(), quick = true)

        state.perform(view, WebAction.CloseMenu)
        emit(pickJson().replace("\"type\":\"pick\"", "\"type\":\"inspect\",\"reason\":\"feedback\""))
        emit("""{"type":"editing","on":true}""")
        await { view.pageEditing }

        assertThat(view.contextMenu).isNull()
        assertThat(view.picked).isNull()
    }

    @Test
    fun `Esc on a feedback popover takes its draft with it`() {
        val view = openView()
        view.beginDraft(ElementTarget("div.card", "", "div", "", emptyList(), "", "", null, null, emptyMap(), null, null, null, null))
        view.contextMenu = ContextMenuState(0.0, 0.0, Offset.Zero, emptyList(), feedback = true)

        state.perform(view, WebAction.CloseMenu)

        assertThat(view.contextMenu).isNull()
        assertThat(view.picked).isNull()
    }

    @Test
    fun `a page pick cancel ends a sticky pick too`() {
        val view = openView()
        ready()
        await { view.pageKey != null }
        state.perform(view, WebAction.StickyPick)

        emit("""{"type":"pickCancel"}""")

        await { !view.toggles.pick }
        assertThat(view.stickyPick).isFalse()
    }

    @Test
    fun `a browser that fails to open is reported and never thrown`() {
        val failing = WebState(service, { throw UnsatisfiedLinkError("no cef") }, { _, _ -> }, { _, _, _, _ -> }, { liveSessions }, scope, { errors += it }, reviews, { _, _ -> })

        val failed = failing.open(1)

        assertThat(failed).isNull()
        assertThat(failing.browserFailure).isEqualTo("no cef")
    }

    private val captured = CopyOnWriteArrayList<List<Any?>>()

    private fun readyToShoot(shot: PageShot? = PageShot(byteArrayOf(1), 10, 10, 10.0, 10.0, 1.0, false)): WebView {
        `when`(reviews.limits()).thenReturn(ReviewService.CaptureLimits(1000, 1_000_000, 1_000_000))
        `when`(reviews.submitCapture(anyLong(), anyString(), anyString(), any(ByteArray::class.java), anyString())).thenAnswer {
            captured += it.arguments.toList()
            Review(7, 1, null, "t", "n", ReviewStatus.PENDING, Instant.EPOCH, null)
        }
        host.page.value = PageState(url = "http://localhost:3000/app?x=1#top", title = "App")
        host.screenshotResult = shot
        return openView()
    }

    @Test
    fun `a screenshot becomes a review and opens it`() {
        val view = readyToShoot()

        state.perform(view, WebAction.Screenshot(ShotArea.WholePage))

        await { opened.isNotEmpty() }
        assertThat(opened).containsExactly(1L to 7L)
        assertThat(host.screenshotCalls).containsExactly(ShotArea.WholePage)
        assertThat(captured.single()[1] as String).startsWith("App - Responsive - ")
        assertThat(captured.single()[2] as String).startsWith("Captured by the user in the Web tab from http://localhost:3000/app, Responsive")
        assertThat(captured.single()[4]).isEqualTo("Whole page")
        await { !view.shooting }
    }

    @Test
    fun `a failed capture or a refused review is reported and nothing opens`() {
        val view = readyToShoot(shot = null)
        state.perform(view, WebAction.Screenshot(ShotArea.Visible))
        await { errors.isNotEmpty() && !view.shooting }

        host.screenshotResult = PageShot(byteArrayOf(1), 10, 10, 10.0, 10.0, 1.0, false)
        `when`(reviews.submitCapture(anyLong(), anyString(), anyString(), any(ByteArray::class.java), anyString())).thenThrow(IllegalArgumentException("Too big"))
        state.perform(view, WebAction.Screenshot(ShotArea.Visible))

        await { errors.contains("Too big") && !view.shooting }
        assertThat(opened).isEmpty()
    }

    @Test
    fun `no screenshot is taken on a blank page or while one is running`() {
        val view = readyToShoot()
        host.page.value = PageState(url = "about:blank")
        state.perform(view, WebAction.Screenshot(ShotArea.Visible))
        state.perform(view, WebAction.StartRegion)
        host.page.value = PageState(url = "http://localhost:3000/")
        view.shooting = true
        state.perform(view, WebAction.Screenshot(ShotArea.Visible))

        assertThat(host.screenshotCalls).isEmpty()
        assertThat(view.regionSelecting).isFalse()
    }

    @Test
    fun `a region replaces pick mode and the menu, and its selection is photographed`() {
        val view = readyToShoot()
        state.setToggles(view, view.toggles.togglePick())
        view.contextMenu = ContextMenuState(1.0, 2.0, Offset.Zero, emptyList())
        host.scroll = PageScroll(0.0, 250.0)

        state.perform(view, WebAction.StartRegion)

        assertThat(view.regionSelecting).isTrue()
        assertThat(view.toggles.pick).isFalse()
        assertThat(view.contextMenu).isNull()
        await { view.regionScroll == PageScroll(0.0, 250.0) }
        host.scroll = PageScroll(0.0, 900.0)
        state.regionSelected(view, CssRect(10.0, 20.0, 30.0, 40.0))
        await { opened.isNotEmpty() }
        assertThat(view.regionSelecting).isFalse()
        assertThat(host.screenshotCalls).containsExactly(ShotArea.Region(10.0, 20.0, 30.0, 40.0, PageScroll(0.0, 250.0)))
        assertThat(captured.single()[4]).isEqualTo("Region")
    }

    @Test
    fun `no screenshot is taken while the page is loading and the user is told`() {
        val view = readyToShoot()
        host.page.value = PageState(url = "http://localhost:3000/", loading = true)

        state.perform(view, WebAction.Screenshot(ShotArea.Visible))
        state.perform(view, WebAction.StartRegion)

        assertThat(host.screenshotCalls).isEmpty()
        assertThat(view.regionSelecting).isFalse()
        assertThat(errors).hasSize(2).allMatch { it.contains("still loading") }
    }

    @Test
    fun `cancelling the region overlay keeps the page untouched`() {
        val view = readyToShoot()
        state.perform(view, WebAction.StartRegion)
        state.perform(view, WebAction.CancelRegion)

        assertThat(view.regionSelecting).isFalse()
        assertThat(host.screenshotCalls).isEmpty()
    }
}
