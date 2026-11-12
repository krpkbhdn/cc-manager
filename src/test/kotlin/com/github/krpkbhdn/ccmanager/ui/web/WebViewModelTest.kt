package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebViewport
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebPage
import com.github.krpkbhdn.ccmanager.web.WebRevision
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import com.github.krpkbhdn.ccmanager.web.StyleChange as StoredStyleChange

class WebViewModelTest {
    private val anchor = ElementAnchor("a.b", "/a", "a", ElementAnchor.SourceHint("Btn", "Btn.tsx", 7), ElementAnchor.Rect(1.0, 2.0, 3.0, 4.0), "", "Go", "<a>", mapOf("color" to "red"))

    private fun revision(id: Long, status: ReviewStatus) = WebRevision(id, 1, id.toInt(), null, null, status, Instant.EPOCH, null, null)

    private fun item(id: Long, pageId: Long, viewport: WebViewport? = null, status: WebItemStatus = WebItemStatus.OPEN) =
        WebItem(id, 1, pageId, id.toInt(), anchor, "c", listOf(StoredStyleChange("color", "red", "blue")), status, null, viewport)

    @Test
    fun `a bare host gets http and a full url is kept`() {
        assertThat(normalizeUrl(" localhost:3000/app ")).isEqualTo("http://localhost:3000/app")
        assertThat(normalizeUrl("https://example.com")).isEqualTo("https://example.com")
        assertThat(normalizeUrl("file:///tmp/a.html")).isEqualTo("file:///tmp/a.html")
        assertThat(normalizeUrl("  ")).isEmpty()
    }

    @Test
    fun `pixel values drop a zero fraction`() {
        assertThat(formatPx(12.0)).isEqualTo("12")
        assertThat(formatPx(12.54)).isEqualTo("12.5")
    }

    @Test
    fun `an anchor maps to a target and back without losing the stored fields`() {
        val target = anchor.toTarget()

        assertThat(target.selector).isEqualTo("a.b")
        assertThat(target.sourceHint).isEqualTo(SourceHint("Btn", "Btn.tsx", 7))
        assertThat(target.frame).isNull()
        assertThat(target.toAnchor()).isEqualTo(anchor)
    }

    @Test
    fun `an item becomes a pin with its stable number and status`() {
        val pin = item(3, 1, status = WebItemStatus.SENT).toPin()

        assertThat(pin.id).isEqualTo("3")
        assertThat(pin.n).isEqualTo(3)
        assertThat(pin.status).isEqualTo("sent")
        assertThat(pin.anchor.selector).isEqualTo("a.b")
    }

    @Test
    fun `editing a stored item starts from its changed values`() {
        assertThat(item(1, 1).editedValues()).containsExactly(entry("color", "blue"))
    }

    @Test
    fun `only the newest revision is expanded and a toggle flips it`() {
        val revisions = listOf(revision(2, ReviewStatus.PENDING), revision(1, ReviewStatus.SENT))
        val items = mapOf(2L to listOf(item(5, 1), item(6, 2)), 1L to listOf(item(4, 1)))
        val pages = listOf(WebPage(1, 1, "k1", "u1", "Home"), WebPage(2, 1, "k2", "u2", ""))

        val rows = itemRows(revisions, { items.getValue(it) }, pages, emptySet())

        assertThat(rows.map { it::class.simpleName }).containsExactly("Header", "PageHeading", "Entry", "PageHeading", "Entry", "Header")
        assertThat(rows.filterIsInstance<ItemsRow.PageHeading>().map { it.title }).containsExactly("Home", "k2")
        val flipped = itemRows(revisions, { items.getValue(it) }, pages, setOf(1L, 2L))
        assertThat(flipped.filterIsInstance<ItemsRow.Header>().map { it.expanded }).containsExactly(false, true)
    }

    @Test
    fun `header counts cover every item while entries follow the device scope`() {
        val revisions = listOf(revision(1, ReviewStatus.PENDING))
        val mobile = WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE)
        val desktop = WebViewport(1280, 720, null, DeviceCategory.DESKTOP)
        val items = listOf(item(1, 1, mobile), item(2, 1, desktop), item(3, 1))
        val rows = itemRows(revisions, { items }, emptyList(), emptySet(), DeviceCategory.MOBILE)

        val header = rows.filterIsInstance<ItemsRow.Header>().single()
        assertThat(header.openCount).isEqualTo(3)
        assertThat(header.total).isEqualTo(3)
        assertThat(header.shown).isEqualTo(2)
        assertThat(rows.filterIsInstance<ItemsRow.Entry>().map { it.item.id() }).containsExactly(1L, 3L)
    }

    @Test
    fun `only the newest finished revision offers the next one and every revision keeps its own status`() {
        val revisions = listOf(revision(2, ReviewStatus.PENDING), revision(1, ReviewStatus.SENT))
        val headers = itemRows(revisions, { emptyList() }, emptyList(), emptySet()).filterIsInstance<ItemsRow.Header>()
        assertThat(headers.map { it.startsNext }).containsExactly(false, false)

        val afterSend = itemRows(listOf(revision(2, ReviewStatus.SENT), revision(1, ReviewStatus.SENT)), { emptyList() }, emptyList(), emptySet())
        assertThat(afterSend.filterIsInstance<ItemsRow.Header>().map { it.startsNext }).containsExactly(true, false)
    }

    @Test
    fun `a draft chip shows the path of its page`() {
        assertThat(draftPageLabel("http://localhost:3000/settings/profile?x=1")).isEqualTo("/settings/profile")
        assertThat(draftPageLabel("about:blank")).isEqualTo("about:blank")
    }
}
