package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jediterm.terminal.CursorShape
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val LIGHT_LINE_DIVISOR = 13f
private const val UNDERLINE_OFFSET_EM = 0.1f
private const val BAR_CURSOR_DIVISOR = 6
private val PRINTABLE_ASCII = 0x20..0x7E
private const val BMP_LIMIT = 0x10000
private val VARIATION_SELECTORS = 0xFE00..0xFE0F

/** Draws the visible part of a session onto a Skia canvas: rows, selection, cursor, all snapped to the cell grid. */
internal class TerminalPainter(val fonts: TerminalFonts, private val paddingX: Int, private val paddingY: Int) {
    private val fill = Paint().apply { isAntiAlias = false }
    private val text = Paint().apply { isAntiAlias = true }
    private val stroke = Paint().apply { isAntiAlias = true; mode = PaintMode.STROKE }
    private val lightWidth = max(1, (fonts.fontSize / LIGHT_LINE_DIVISOR).roundToInt())
    private val cellWidth = fonts.cellWidth
    private val cellHeight = fonts.cellHeight

    fun paint(canvas: Canvas, width: Int, height: Int, session: TerminalSession, cursorFilled: Boolean) {
        val theme = session.theme
        canvas.clear(theme.background.toArgb())
        val scroll = session.scrollShown
        val firstLine = -ceil(scroll).toInt()
        val lastLine = session.rows - 1 - floor(scroll).toInt()
        val lines = (firstLine..lastLine).toList()
        val rows = session.buffer.withLock { lines.map { rowOf(session, it, cursorFilled) } }
        canvas.save()
        canvas.clipRect(Rect.makeXYWH(0f, paddingY.toFloat(), width.toFloat(), (height - 2 * paddingY).toFloat()), ClipMode.INTERSECT)
        val tops = lines.map { paddingY + ((it + scroll) * cellHeight).roundToInt() }
        rows.forEachIndexed { index, runs -> drawBackgrounds(canvas, runs, tops[index], theme.background) }
        rows.forEachIndexed { index, runs -> drawText(canvas, runs, tops[index]) }
        drawCursorShape(canvas, session, cursorFilled, scroll)
        canvas.restore()
    }

    private fun rowOf(session: TerminalSession, line: Int, cursorFilled: Boolean): List<Run> {
        if (line < -session.scrollbackLines || line >= session.rows) return emptyList()
        val theme = session.theme
        var runs = runsOf(session.buffer.getLine(line), theme)
        session.selection?.columnsIn(line, session.columns)?.let { columns ->
            runs = restyled(runs, columns) { it.copy(foreground = theme.foreground, background = theme.selection) }
        }
        val cursor = blockCursorColumn(session, line, cursorFilled)
        if (cursor != null) {
            runs = restyled(padTo(runs, cursor, theme), cursor..cursor) { it.copy(foreground = theme.background, background = theme.foreground) }
        }
        return runs
    }

    private fun blockCursorColumn(session: TerminalSession, line: Int, cursorFilled: Boolean): Int? {
        val modes = session.modes
        val isBlock = modes.cursorShape.let { it == null || it == CursorShape.BLINK_BLOCK || it == CursorShape.STEADY_BLOCK }
        val visible = modes.cursorVisible && cursorFilled && isBlock && line == modes.cursorRow
        return modes.cursorColumn.coerceIn(0, session.columns - 1).takeIf { visible }
    }

    private fun padTo(runs: List<Run>, column: Int, theme: TerminalTheme): List<Run> {
        val end = runs.lastOrNull()?.end ?: 0
        if (end > column) return runs
        val blank = CellPaint(theme.foreground, theme.background)
        return runs + Run(end, " ".repeat(column + 1 - end), blank)
    }

    private fun drawBackgrounds(canvas: Canvas, runs: List<Run>, top: Int, defaultBackground: Color) {
        for (run in runs) {
            if (run.paint.background == defaultBackground) continue
            fill.color = run.paint.background.toArgb()
            canvas.drawRect(Rect.makeLTRB((paddingX + run.column * cellWidth).toFloat(), top.toFloat(), (paddingX + run.end * cellWidth).toFloat(), (top + cellHeight).toFloat()), fill)
        }
    }

    private fun drawText(canvas: Canvas, runs: List<Run>, top: Int) {
        val baseline = top + fonts.baseline
        for (run in runs) {
            text.color = run.paint.foreground.toArgb()
            if (run.text.all { it.code in PRINTABLE_ASCII }) drawSegment(canvas, run, 0, run.text.length, baseline) else drawMixed(canvas, run, top, baseline)
            if (run.paint.underline) drawUnderline(canvas, run, baseline)
        }
    }

    private fun drawMixed(canvas: Canvas, run: Run, top: Int, baseline: Float) {
        var segmentStart = 0
        var segmentEnd = 0
        for (cluster in clustersOf(run.text)) {
            if (isRunGlyph(cluster, run.paint.styleBits)) {
                if (segmentEnd == segmentStart) segmentStart = cluster.offset
                segmentEnd = cluster.offset + cluster.cells
                continue
            }
            drawSegment(canvas, run, segmentStart, segmentEnd, baseline)
            segmentStart = cluster.offset + cluster.cells
            segmentEnd = segmentStart
            drawCluster(canvas, run, cluster, top, baseline)
        }
        drawSegment(canvas, run, segmentStart, segmentEnd, baseline)
    }

    private fun isRunGlyph(cluster: Cluster, styleBits: Int) =
        cluster.cells == 1 && cluster.codePoint < BMP_LIMIT && !isGeometric(cluster.codePoint) && fonts.isRunGlyph(cluster.codePoint, styleBits)

    private fun drawSegment(canvas: Canvas, run: Run, from: Int, to: Int, baseline: Float) {
        if (from >= to) return
        val segment = run.text.substring(from, to)
        if (segment.isBlank()) return
        val x = (paddingX + (run.column + from) * cellWidth).toFloat()
        canvas.drawString(segment, x, baseline, fonts.face(run.paint.styleBits), text)
    }

    private fun drawCluster(canvas: Canvas, run: Run, cluster: Cluster, top: Int, baseline: Float) {
        val left = paddingX + (run.column + cluster.offset) * cellWidth
        val boxWidth = cluster.cells * cellWidth
        val shape = boxShape(cluster.codePoint, boxWidth, cellHeight, lightWidth)
        if (shape != null) return drawShape(canvas, shape, left, top)
        if (cluster.codePoint == ' '.code || isInvisible(cluster.codePoint)) return
        val glyph = String(Character.toChars(cluster.codePoint))
        val font = fonts.faceFor(cluster.codePoint, run.paint.styleBits)
        val advance = font.measureTextWidth(glyph)
        val scale = if (advance > boxWidth) boxWidth / advance else 1f
        canvas.save()
        canvas.translate(left + (boxWidth - advance * scale) / 2, baseline)
        canvas.scale(scale, scale)
        canvas.drawString(glyph, 0f, 0f, font, text)
        canvas.restore()
    }

    /** Variation selectors and zero-width joiners take a buffer cell but show nothing. */
    private fun isInvisible(codePoint: Int) = codePoint in VARIATION_SELECTORS || Character.getType(codePoint) == Character.FORMAT.toInt()

    private fun drawShape(canvas: Canvas, shape: BoxShape, left: Int, top: Int) {
        val color = text.color
        fill.color = withOpacity(color, shape.opacity)
        for (rect in shape.rects) {
            canvas.drawRect(Rect.makeLTRB((left + rect.left).toFloat(), (top + rect.top).toFloat(), (left + rect.right).toFloat(), (top + rect.bottom).toFloat()), fill)
        }
        val arc = shape.arc ?: return
        stroke.color = color
        stroke.strokeWidth = arc.width
        val path = PathBuilder()
            .moveTo(left + arc.startX, top + arc.startY)
            .quadTo(left + arc.controlX, top + arc.controlY, left + arc.endX, top + arc.endY)
            .detach()
        canvas.drawPath(path, stroke)
    }

    private fun withOpacity(argb: Int, opacity: Float): Int = ((argb ushr 24) * opacity).roundToInt() shl 24 or (argb and 0xFFFFFF)

    private fun drawUnderline(canvas: Canvas, run: Run, baseline: Float) {
        fill.color = run.paint.foreground.toArgb()
        val y = baseline + max(1f, fonts.fontSize * UNDERLINE_OFFSET_EM).roundToInt()
        canvas.drawRect(Rect.makeLTRB((paddingX + run.column * cellWidth).toFloat(), y, (paddingX + run.end * cellWidth).toFloat(), y + lightWidth), fill)
    }

    private fun drawCursorShape(canvas: Canvas, session: TerminalSession, cursorFilled: Boolean, scroll: Float) {
        val modes = session.modes
        if (!modes.cursorVisible) return
        val shape = modes.cursorShape ?: CursorShape.STEADY_BLOCK
        val isBlock = shape == CursorShape.BLINK_BLOCK || shape == CursorShape.STEADY_BLOCK
        if (isBlock && cursorFilled) return
        val column = modes.cursorColumn.coerceIn(0, session.columns - 1)
        val left = paddingX + column * cellWidth
        val top = paddingY + ((modes.cursorRow + scroll) * cellHeight).roundToInt()
        val color = session.theme.foreground.toArgb()
        fill.color = color
        when {
            isBlock -> drawOutline(canvas, left, top, color)
            shape == CursorShape.BLINK_UNDERLINE || shape == CursorShape.STEADY_UNDERLINE ->
                canvas.drawRect(Rect.makeLTRB(left.toFloat(), (top + cellHeight - lightWidth).toFloat(), (left + cellWidth).toFloat(), (top + cellHeight).toFloat()), fill)
            else -> canvas.drawRect(Rect.makeLTRB(left.toFloat(), top.toFloat(), (left + max(lightWidth, cellWidth / BAR_CURSOR_DIVISOR)).toFloat(), (top + cellHeight).toFloat()), fill)
        }
    }

    private fun drawOutline(canvas: Canvas, left: Int, top: Int, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1f
        canvas.drawRect(Rect.makeLTRB(left + 0.5f, top + 0.5f, left + cellWidth - 0.5f, top + cellHeight - 0.5f), stroke)
    }
}
