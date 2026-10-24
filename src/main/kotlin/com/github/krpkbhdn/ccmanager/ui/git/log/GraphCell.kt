package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.theme.colors

val GraphLaneWidth = 14.dp
private val strokeWidth = 2.dp
private val dotRadius = 4.5.dp
private val ringWidth = 2.dp

/**
 * The graph column of one row: the lower halves of this row's links, the upper halves of the previous row's links and
 * the commit dot (hollow for merges). Rows stay independent so the list can be lazy.
 */
@Composable
fun GraphCell(row: GitLog.Row, previous: GitLog.Row?, laneCount: Int, rowHeight: Dp, modifier: Modifier = Modifier) {
    val dark = colors.dark
    val surface = colors.background
    Canvas(modifier.width(GraphLaneWidth * maxOf(laneCount, 1)).height(rowHeight)) {
        val laneWidth = GraphLaneWidth.toPx()
        val height = size.height
        val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        previous?.links()?.forEach { link -> drawCubic(linkSegments(link.fromLane(), link.toLane(), height, laneWidth).upper, graphColor(link.color(), dark), stroke) }
        row.links().forEach { link -> drawCubic(linkSegments(link.fromLane(), link.toLane(), height, laneWidth).lower, graphColor(link.color(), dark), stroke) }
        drawDot(Offset(laneX(row.lane(), laneWidth), height / 2f), graphColor(row.color(), dark), hollow = row.commit().parents().size > 1, surface)
    }
}

private fun DrawScope.drawCubic(c: Cubic, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(c.x0, c.y0)
        cubicTo(c.x1, c.y1, c.x2, c.y2, c.x3, c.y3)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawDot(center: Offset, color: Color, hollow: Boolean, surface: Color) {
    val radius = dotRadius.toPx()
    if (hollow) {
        drawCircle(surface, radius, center)
        drawCircle(color, radius - ringWidth.toPx() / 2f, center, style = Stroke(ringWidth.toPx()))
    } else {
        drawCircle(surface, radius + 1.5.dp.toPx(), center)
        drawCircle(color, radius, center)
    }
}
