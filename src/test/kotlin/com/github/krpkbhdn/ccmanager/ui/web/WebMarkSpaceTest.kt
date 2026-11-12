package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import com.github.krpkbhdn.ccmanager.web.ElementAnchor
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebMarkShape
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebMarkSpaceTest {
    private val pans = mutableListOf<Pair<Double, Double>>()
    private val zooms = mutableListOf<Pair<Offset, Int>>()

    private fun space(layout: PageViewport?, scroll: DocScroll?, density: Float = 1f) =
        WebMarkSpace({ layout }, { scroll }, { density }, { dx, dy -> pans += dx to dy }, { at, n -> zooms += at to n })

    @Test
    fun `a page at 100 percent maps document px to canvas px minus the scroll`() {
        val viewport = space(PageViewport(1000, 700), DocScroll(0.0, 300.0, 1000.0, 2000.0)).viewport!!

        assertThat(viewport.toScreen(100f, 400f)).isEqualTo(100f to 100f)
        assertThat(viewport.toImage(100f, 100f)).isEqualTo(100f to 400f)
    }

    @Test
    fun `zoom placement and density all enter the mapping`() {
        val page = PageViewport(390, 844, scale = 0.5, offsetX = 40.0, offsetY = 10.0)
        val viewport = space(page, DocScroll(20.0, 100.0, 390.0, 3000.0), density = 2f).viewport!!

        assertThat(viewport.scale).isEqualTo(1f)
        // document (120, 300) is page point (100, 200) after the scroll: 0.5 dp per css px, 2 px per dp, plus the placement.
        assertThat(viewport.toScreen(120f, 300f)).isEqualTo(180f to 220f)
        assertThat(viewport.toImage(180f, 220f)).isEqualTo(120f to 300f)
    }

    @Test
    fun `the document size is the image size and an unknown or empty one gives no view`() {
        val doc = DocScroll(0.0, 0.0, 800.0, 2400.0)

        assertThat(space(PageViewport(800, 600), doc).imageW).isEqualTo(800f)
        assertThat(space(PageViewport(800, 600), doc).imageH).isEqualTo(2400f)
        assertThat(space(PageViewport(800, 600), null).viewport).isNull()
        assertThat(space(null, doc).viewport).isNull()
        assertThat(space(PageViewport(800, 600), DocScroll(0.0, 0.0, 0.0, 0.0)).viewport).isNull()
    }

    @Test
    fun `panning follows the pointer so the page pan goes the other way in dp`() {
        space(PageViewport(800, 600), DocScroll(0.0, 0.0, 800.0, 600.0), density = 2f).panBy(20f, -10f)

        assertThat(pans).containsExactly(-10.0 to 5.0)
    }

    @Test
    fun `zooming reports the cursor in dp and the direction`() {
        val s = space(PageViewport(800, 600), DocScroll(0.0, 0.0, 800.0, 600.0), density = 2f)

        s.zoomAt(100f, 60f, 1.1f)
        s.zoomAt(100f, 60f, 0.9f)

        assertThat(zooms).containsExactly(Offset(50f, 30f) to 1, Offset(50f, 30f) to -1)
    }

    @Test
    fun `a shape becomes a normalised mark and back to the same document px`() {
        val shape = WebMarkShape(MarkKind.RECTANGLE, 100.0, 200.0, 300.0, 400.0)

        val mark = shape.toMark(1000.0, 2000.0, "note")

        assertThat(mark).isEqualTo(MarkDraft(MarkKind.RECTANGLE, 0.1, 0.1, 0.3, 0.2, "note"))
        assertThat(mark.toShape(1000.0, 2000.0)).isEqualTo(shape)
    }

    @Test
    fun `a freeform shape keeps its points through the conversion`() {
        val points = listOf(MarkPoint(16.0, 16.0), MarkPoint(112.0, 16.0), MarkPoint(64.0, 80.0))
        val shape = WebMarkShape(MarkKind.FREEFORM, 16.0, 16.0, 96.0, 64.0, points)

        assertThat(shape.toMark(128.0, 128.0).toShape(128.0, 128.0)).isEqualTo(shape)
    }

    @Test
    fun `a mark beyond the document is clamped into it`() {
        val shape = MarkDraft(MarkKind.RECTANGLE, 0.9, -0.1, 0.5, 2.0, "").toShape(1000.0, 500.0)!!

        assertThat(shape.x).isEqualTo(900.0)
        assertThat(shape.y).isEqualTo(0.0)
        assertThat(shape.w).isEqualTo(100.0)
        assertThat(shape.h).isEqualTo(500.0)
    }

    @Test
    fun `a polygon with too many points is no shape`() {
        val points = (0..WebMarkShape.MAX_POINTS).map { MarkPoint(it / 1000.0, (it % 7) / 10.0) }

        assertThat(MarkDraft(MarkKind.FREEFORM, 0.0, 0.0, 0.5, 0.6, "", points).toShape(1000.0, 1000.0)).isNull()
    }

    @Test
    fun `the crop surrounds the shape with a margin inside the viewport`() {
        val shape = WebMarkShape(MarkKind.RECTANGLE, 100.0, 200.0, 200.0, 200.0)

        assertThat(cropAround(shape, DocScroll(0.0, 100.0, 1000.0, 2000.0), 1000.0, 700.0)).isEqualTo(Rect(76.0, 76.0, 248.0, 248.0))
        assertThat(cropAround(shape, DocScroll(0.0, 100.0, 1000.0, 2000.0), 200.0, 150.0)).isEqualTo(Rect(76.0, 76.0, 124.0, 74.0))
    }

    @Test
    fun `a shape scrolled out of the viewport has no crop`() {
        assertThat(cropAround(WebMarkShape(MarkKind.PIN, 100.0, 1500.0, 0.0, 0.0), DocScroll(0.0, 0.0, 1000.0, 2000.0), 1000.0, 700.0)).isNull()
    }

    @Test
    fun `the mark in a crop is shifted and scaled to the screenshot pixels`() {
        val shape = WebMarkShape(MarkKind.RECTANGLE, 100.0, 200.0, 200.0, 200.0)

        assertThat(shape.inCrop(Rect(76.0, 176.0, 248.0, 248.0), 2.0)).isEqualTo(WebMarkShape(MarkKind.RECTANGLE, 48.0, 48.0, 400.0, 400.0))
    }

    @Test
    fun `go to scrolls the middle of the shape to the middle of the viewport`() {
        val shape = WebMarkShape(MarkKind.RECTANGLE, 100.0, 1000.0, 200.0, 100.0)

        assertThat(scrollDeltaTo(shape, DocScroll(0.0, 200.0, 1000.0, 3000.0), 1000.0, 700.0)).isEqualTo(-300.0 to 500.0)
    }

    private fun item(id: Long, shape: WebMarkShape?) =
        WebItem(id, 1, 1, id.toInt(), ElementAnchor("", null, null, null, null, null, null, null, null), "c", emptyList(), WebItemStatus.OPEN, null, null, shape)

    @Test
    fun `a pending mark settles once the stored item shows its shape or the item is gone`() {
        val doc = DocScroll(0.0, 0.0, 1000.0, 1000.0)
        val saved = WebMarkShape(MarkKind.RECTANGLE, 100.0, 100.0, 200.0, 200.0)
        val pending = mapOf(
            1L to saved.toMark(1000.0, 1000.0),
            2L to MarkDraft(MarkKind.RECTANGLE, 0.5, 0.5, 0.1, 0.1, ""),
            3L to MarkDraft(MarkKind.RECTANGLE, 0.5, 0.5, 0.1, 0.1, ""),
        )

        val settled = settledMarks(pending, listOf(item(1, saved), item(2, saved)), doc)

        assertThat(settled).containsExactlyInAnyOrder(1L, 3L)
    }
}
