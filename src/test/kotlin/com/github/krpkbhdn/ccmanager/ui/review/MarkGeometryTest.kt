package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkGeometryTest {
    private fun rect(x: Double, y: Double, w: Double, h: Double) = MarkDraft(MarkKind.RECTANGLE, x, y, w, h, "")
    private fun ellipse(x: Double, y: Double, w: Double, h: Double) = MarkDraft(MarkKind.ELLIPSE, x, y, w, h, "")

    @Test
    fun `a drag becomes a normalised rectangle`() {
        val mark = MarkGeometry.rectFromDrag(100f, 50f, 300f, 150f, 1000f, 500f)!!
        assertThat(mark).isEqualTo(rect(0.1, 0.1, 0.2, 0.2))
    }

    @Test
    fun `a reversed drag gives the same rectangle`() {
        assertThat(MarkGeometry.rectFromDrag(300f, 150f, 100f, 50f, 1000f, 500f))
            .isEqualTo(MarkGeometry.rectFromDrag(100f, 50f, 300f, 150f, 1000f, 500f))
    }

    @Test
    fun `a tiny drag is ignored`() {
        assertThat(MarkGeometry.rectFromDrag(10f, 10f, 13f, 200f, 1000f, 500f)).isNull()
    }

    @Test
    fun `a drag past the image is clamped to the bounds`() {
        val mark = MarkGeometry.rectFromDrag(-50f, 400f, 2000f, 900f, 1000f, 500f)!!
        assertThat(mark).isEqualTo(rect(0.0, 0.8, 1.0, 0.2))
    }

    @Test
    fun `a stroke becomes an ellipse over its bounding box`() {
        val stroke = listOf(100f to 100f, 300f to 120f, 200f to 200f)
        assertThat(MarkGeometry.ellipseFromStroke(stroke, 1000f, 1000f)).isEqualTo(ellipse(0.1, 0.1, 0.2, 0.1))
    }

    @Test
    fun `a tiny or empty stroke is ignored`() {
        assertThat(MarkGeometry.ellipseFromStroke(listOf(5f to 5f, 7f to 6f), 1000f, 1000f)).isNull()
        assertThat(MarkGeometry.ellipseFromStroke(emptyList(), 1000f, 1000f)).isNull()
    }

    @Test
    fun `a pin has no size and an empty comment`() {
        assertThat(MarkGeometry.pinAt(0.3f, 0.4f).let { listOf(it.kind, it.w, it.h, it.comment) })
            .containsExactly(MarkKind.PIN, 0.0, 0.0, "")
    }

    @Test
    fun `an ellipse is hit inside but not in its bounding box corner`() {
        val marks = listOf(ellipse(0.0, 0.0, 1.0, 1.0))
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.5f, 100f, 100f, 1f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.02f, 0.02f, 100f, 100f, 1f)).isNull()
    }

    @Test
    fun `a pin is hit within the tolerance only`() {
        val marks = listOf(MarkGeometry.pinAt(0.5f, 0.5f))
        assertThat(MarkGeometry.hitTest(marks, 0.51f, 0.5f, 100f, 100f, 2f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.6f, 0.5f, 100f, 100f, 2f)).isNull()
    }

    @Test
    fun `a pin hit area is a circle on a wide image`() {
        val marks = listOf(MarkGeometry.pinAt(0.5f, 0.5f))
        assertThat(MarkGeometry.hitTest(marks, 0.503f, 0.5f, 1000f, 100f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.53f, 1000f, 100f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.51f, 0.5f, 1000f, 100f, 5f)).isNull()
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.6f, 1000f, 100f, 5f)).isNull()
    }

    @Test
    fun `a thin rectangle and ellipse are hit within the tolerance of their outline`() {
        val thin = listOf(rect(0.1, 0.5, 0.8, 0.0))
        assertThat(MarkGeometry.hitTest(thin, 0.5f, 0.503f, 1000f, 1000f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(thin, 0.5f, 0.52f, 1000f, 1000f, 5f)).isNull()
        val ring = listOf(ellipse(0.2, 0.2, 0.6, 0.6))
        assertThat(MarkGeometry.hitTest(ring, 0.803f, 0.5f, 1000f, 1000f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(ring, 0.82f, 0.5f, 1000f, 1000f, 5f)).isNull()
    }

    @Test
    fun `on overlap the smallest mark wins`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2), rect(0.0, 0.0, 1.0, 1.0))
        assertThat(MarkGeometry.hitTest(marks, 0.2f, 0.2f, 100f, 100f, 1f)).isEqualTo(0)
    }

    @Test
    fun `equal marks resolve to the topmost one`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2), rect(0.1, 0.1, 0.2, 0.2))
        assertThat(MarkGeometry.hitTest(marks, 0.2f, 0.2f, 100f, 100f, 1f)).isEqualTo(1)
    }

    private fun triangle() = MarkDraft(MarkKind.FREEFORM, 0.2, 0.2, 0.6, 0.6, "", listOf(MarkPoint(0.2, 0.2), MarkPoint(0.8, 0.2), MarkPoint(0.5, 0.8)))

    @Test
    fun `a freeform is hit inside and near an edge but not far outside`() {
        val marks = listOf(triangle())
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.4f, 1000f, 1000f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.197f, 1000f, 1000f, 5f)).isEqualTo(0)
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.1f, 1000f, 1000f, 5f)).isNull()
        assertThat(MarkGeometry.hitTest(marks, 0.25f, 0.7f, 1000f, 1000f, 5f)).isNull()
    }

    @Test
    fun `a freeform ranks by polygon area not by bounding box`() {
        val marks = listOf(rect(0.0, 0.0, 1.0, 1.0), triangle(), rect(0.3, 0.2, 0.4, 0.5))
        assertThat(MarkGeometry.hitTest(marks, 0.5f, 0.3f, 1000f, 1000f, 1f)).isEqualTo(1)
    }

    @Test
    fun `labels start at one`() {
        assertThat(MarkGeometry.labelOf(0)).isEqualTo("1")
    }
}
