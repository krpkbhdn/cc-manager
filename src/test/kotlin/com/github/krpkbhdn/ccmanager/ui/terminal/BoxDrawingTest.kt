package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val W = 8
private const val H = 18

class BoxDrawingTest {
    private fun shape(char: Char, light: Int = 1) = boxShape(char.code, W, H, light)!!

    private fun covered(shape: BoxShape): Set<Pair<Int, Int>> =
        shape.rects.flatMapTo(HashSet()) { r -> (r.left until r.right).flatMap { x -> (r.top until r.bottom).map { y -> x to y } } }

    @Test
    fun fullAndHalfBlocksFillExactCellFractions() {
        assertThat(shape('█').rects).containsExactly(PixelRect(0, 0, W, H))
        assertThat(shape('▀').rects).containsExactly(PixelRect(0, 0, W, H / 2))
        assertThat(shape('▄').rects).containsExactly(PixelRect(0, H / 2, W, H))
        assertThat(shape('▌').rects).containsExactly(PixelRect(0, 0, W / 2, H))
        assertThat(shape('▐').rects).containsExactly(PixelRect(W / 2, 0, W, H))
        assertThat(shape('▁').rects.single().let { it.bottom - it.top }).isEqualTo(2)
    }

    @Test
    fun shadesKeepTheCellFullAtPartialOpacity() {
        assertThat(shape('░').opacity).isEqualTo(0.25f)
        assertThat(shape('▒').opacity).isEqualTo(0.5f)
        assertThat(shape('▓').rects).containsExactly(PixelRect(0, 0, W, H))
    }

    @Test
    fun quadrantsCombineTheFourCorners() {
        assertThat(covered(shape('▘'))).hasSize(W * H / 4)
        assertThat(covered(shape('▚'))).hasSize(W * H / 2)
        assertThat(covered(shape('▛'))).hasSize(W * H * 3 / 4)
    }

    @Test
    fun lightHorizontalSpansTheWholeCellWidthWithoutGaps() {
        val covered = covered(shape('─'))
        assertThat((0 until W).all { x -> covered.count { it.first == x } == 1 }).isTrue()
        assertThat(covered.map { it.second }.toSet()).hasSize(1)
    }

    @Test
    fun verticalAndHorizontalLinesMeetAtTheCenterOfACross() {
        val cross = covered(shape('┼'))
        assertThat(cross).containsAll(covered(shape('─'))).containsAll(covered(shape('│')))
    }

    @Test
    fun cornersConnectOnlyTheirTwoSides() {
        val corner = covered(shape('┌'))
        assertThat(corner.any { it.first == W - 1 }).isTrue()
        assertThat(corner.any { it.second == H - 1 }).isTrue()
        assertThat(corner.none { it.first == 0 }).isTrue()
        assertThat(corner.none { it.second == 0 }).isTrue()
    }

    @Test
    fun heavyLinesAreThickerThanLightOnes() {
        assertThat(covered(shape('━', 2)).size).isGreaterThan(covered(shape('─', 2)).size)
    }

    @Test
    fun doubleLinesDrawTwoStrokes() {
        assertThat(covered(shape('═')).map { it.second }.toSet()).hasSize(3 * 1 - 1)
    }

    @Test
    fun roundedCornersAreArcsBetweenTheTwoCellEdges() {
        val arc = shape('╭').arc!!
        assertThat(arc.startX).isEqualTo(W.toFloat())
        assertThat(arc.endY).isEqualTo(H.toFloat())
        assertThat(shape('╯').arc!!.let { it.startX to it.endY }).isEqualTo(0f to 0f)
    }

    @Test
    fun ordinaryCharactersAreNotGeometric() {
        assertThat(isGeometric('a'.code)).isFalse()
        assertThat(isGeometric('⏵'.code)).isFalse()
        assertThat(isGeometric('╱'.code)).isFalse()
        assertThat(isGeometric('─'.code)).isTrue()
        assertThat(isGeometric('█'.code)).isTrue()
    }
}
