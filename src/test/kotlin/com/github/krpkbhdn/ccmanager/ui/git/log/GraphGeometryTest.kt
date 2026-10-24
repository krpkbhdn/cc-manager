package com.github.krpkbhdn.ccmanager.ui.git.log

import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

class GraphGeometryTest {
    private val lane = 14f
    private val height = 30f
    private val tolerance = Offset.offset(0.001f)

    @Test
    fun `lanes sit at the centre of their column`() {
        assertThat(laneX(0, lane)).isEqualTo(7f)
        assertThat(laneX(3, lane)).isEqualTo(49f)
        assertThat(graphWidth(4, lane)).isEqualTo(56f)
        assertThat(graphWidth(0, lane)).isEqualTo(14f)
    }

    @Test
    fun `a vertical link is a straight line through the row boundary`() {
        val link = linkSegments(2, 2, height, lane)

        assertThat(link.lower.x0).isEqualTo(laneX(2, lane))
        assertThat(listOf(link.lower.x1, link.lower.x2, link.lower.x3, link.upper.x0, link.upper.x3)).allMatch { it == laneX(2, lane) }
        assertThat(link.lower.y0).isEqualTo(height / 2)
        assertThat(link.lower.y3).isEqualTo(height)
        assertThat(link.upper.y0).isEqualTo(0f)
        assertThat(link.upper.y3).isEqualTo(height / 2)
    }

    @Test
    fun `the two halves of a diagonal link meet at the same point of the row boundary`() {
        val link = linkSegments(0, 3, height, lane)

        assertThat(link.lower.x3).isCloseTo(link.upper.x0, tolerance)
        assertThat(link.lower.x3).isCloseTo((laneX(0, lane) + laneX(3, lane)) / 2, tolerance)
        assertThat(link.lower.y3).isEqualTo(height)
        assertThat(link.upper.y0).isEqualTo(0f)
        assertThat(link.lower.x0).isEqualTo(laneX(0, lane))
        assertThat(link.upper.x3).isEqualTo(laneX(3, lane))
    }

    @Test
    fun `curves leave and arrive vertically`() {
        val link = linkSegments(1, 4, height, lane)

        assertThat(link.lower.x1).isEqualTo(link.lower.x0)
        assertThat(link.upper.x2).isEqualTo(link.upper.x3)
    }

    @Test
    fun `palette wraps over eight slots and differs per theme`() {
        assertThat(graphColor(0, true)).isEqualTo(graphColor(GRAPH_COLOR_COUNT, true))
        assertThat((0 until GRAPH_COLOR_COUNT).map { graphColor(it, true) }.toSet()).hasSize(GRAPH_COLOR_COUNT)
        assertThat((0 until GRAPH_COLOR_COUNT).map { graphColor(it, false) }.toSet()).hasSize(GRAPH_COLOR_COUNT)
        assertThat(graphColor(2, true)).isNotEqualTo(graphColor(2, false))
        assertThat(graphColor(-1, true)).isEqualTo(graphColor(GRAPH_COLOR_COUNT - 1, true))
    }

    @Test
    fun `the sample history lays out with valid lanes and links`() {
        val rows = SampleRepos.busy().rows

        assertThat(rows).hasSizeGreaterThan(30)
        assertThat(rows.maxOf { it.laneCount() }).isBetween(3, 6)
        rows.forEach { row ->
            assertThat(row.lane()).isLessThan(row.laneCount())
            row.links().forEach { link -> assertThat(maxOf(link.fromLane(), link.toLane())).isLessThan(maxOf(row.laneCount(), rows.maxOf { it.laneCount() })) }
        }
        assertThat(rows.count { it.commit().parents().size > 1 }).isGreaterThanOrEqualTo(4)
    }
}
