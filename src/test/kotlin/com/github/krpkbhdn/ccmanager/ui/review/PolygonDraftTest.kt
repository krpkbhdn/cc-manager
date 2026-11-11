package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class PolygonDraftTest {
    private val size = 1000f

    private fun draftOf(vararg p: Pair<Double, Double>) = PolygonDraft().also { d -> p.forEach { d.add(it.first, it.second, size, size, 2f) } }

    private fun square() = draftOf(0.1 to 0.1, 0.4 to 0.1, 0.4 to 0.4, 0.1 to 0.4)

    @Test
    fun `points are clamped to the image`() {
        val draft = draftOf(-0.5 to 2.0)
        assertThat(draft.points.single().x).isEqualTo(0.0)
        assertThat(draft.points.single().y).isEqualTo(1.0)
    }

    @Test
    fun `a point closer than the gap to the previous one is ignored`() {
        val draft = draftOf(0.1 to 0.1)
        assertThat(draft.add(0.1005, 0.1, size, size, 2f)).isFalse()
        assertThat(draft.add(0.2, 0.1, size, size, 2f)).isTrue()
        assertThat(draft.points).hasSize(2)
    }

    @Test
    fun `removeLast drops the newest point and an empty draft stays usable`() {
        val draft = draftOf(0.1 to 0.1, 0.4 to 0.1)
        draft.removeLast()
        draft.removeLast()
        draft.removeLast()
        assertThat(draft.isEmpty).isTrue()
        assertThat(draft.add(0.2, 0.2, size, size, 2f)).isTrue()
    }

    @Test
    fun `closing needs three points and the first point within the tolerance`() {
        assertThat(draftOf(0.1 to 0.1, 0.4 to 0.1).closableAt(0.1, 0.1, size, size, 10f)).isFalse()
        val draft = square()
        assertThat(draft.closableAt(0.105, 0.1, size, size, 10f)).isTrue()
        assertThat(draft.closableAt(0.15, 0.1, size, size, 10f)).isFalse()
        assertThat(draft.nearFirst(0.1, 0.105, size, size, 10f)).isTrue()
    }

    @Test
    fun `close gives a freeform mark with its bounding box and an empty comment`() {
        val mark = square().close(size, size)!!
        assertThat(mark.kind).isEqualTo(MarkKind.FREEFORM)
        assertThat(mark.points).hasSize(4)
        assertThat(mark.comment).isEmpty()
        assertThat(mark.x).isEqualTo(0.1)
        assertThat(mark.w).isEqualTo(0.3, within(1e-9))
    }

    @Test
    fun `fewer than three points or a degenerate area gives no mark`() {
        assertThat(draftOf(0.1 to 0.1, 0.4 to 0.1).close(size, size)).isNull()
        assertThat(draftOf(0.1 to 0.1, 0.4 to 0.1, 0.7 to 0.1).close(size, size)).isNull()
        assertThat(draftOf(0.1 to 0.1, 0.1005 to 0.1, 0.1 to 0.1007).close(size, size)).isNull()
    }
}
