package com.github.krpkbhdn.ccmanager.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

private const val CORNER_RATIO = 0.24
private const val STROKE_RATIO = 0.085f
private val clayTop = Color(0xF0, 0x9A, 0x74)
private val clayBottom = Color(0xB8, 0x51, 0x2F)
private const val BADGE_RATIO = 0.42
private const val BADGE_RING = 0.14
private val badgeRed = Color(0xE5, 0x39, 0x35)
private const val MAX_BADGE_DIGIT = 9
private const val BADGE_TEXT_RATIO = 0.7f
private val sheen = Color(255, 255, 255, 64)

/** App icon drawn in code: a clay-gradient rounded square with a terminal prompt, so no image resource is shipped. */
fun drawAppIcon(size: Int, waiting: Int = 0): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    val s = size.toDouble()
    val shape = RoundRectangle2D.Double(0.0, 0.0, s, s, s * CORNER_RATIO * 2, s * CORNER_RATIO * 2)
    g.paint = GradientPaint(0f, 0f, clayTop, 0f, size.toFloat(), clayBottom)
    g.fill(shape)
    g.clip = shape
    g.paint = GradientPaint(0f, 0f, sheen, 0f, size * 0.5f, Color(255, 255, 255, 0))
    g.fillRect(0, 0, size, size / 2)
    g.clip = null
    g.color = Color.WHITE
    g.stroke = BasicStroke(size * STROKE_RATIO, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val chevron = Path2D.Double().apply {
        moveTo(s * 0.28, s * 0.34)
        lineTo(s * 0.48, s * 0.5)
        lineTo(s * 0.28, s * 0.66)
    }
    g.draw(chevron)
    g.draw(java.awt.geom.Line2D.Double(s * 0.54, s * 0.68, s * 0.74, s * 0.68))
    if (waiting > 0) {
        val d = s * BADGE_RATIO
        g.color = Color.WHITE
        g.fill(java.awt.geom.Ellipse2D.Double(s - d, 0.0, d, d))
        g.color = badgeRed
        val ring = d * BADGE_RING
        g.fill(java.awt.geom.Ellipse2D.Double(s - d + ring, ring, d - 2 * ring, d - 2 * ring))
        drawBadgeCount(g, badgeText(waiting), s - d, d)
    }
    g.dispose()
    return image
}

/** "3", or "9+" past one digit. */
fun badgeText(count: Int): String = if (count > MAX_BADGE_DIGIT) "$MAX_BADGE_DIGIT+" else "$count"

/** A red circle with the count, for the taskbar button overlay. */
fun drawBadgeOverlay(size: Int, count: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = badgeRed
    g.fill(java.awt.geom.Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
    drawBadgeCount(g, badgeText(count), 0.0, size.toDouble())
    g.dispose()
    return image
}

private fun drawBadgeCount(g: java.awt.Graphics2D, text: String, left: Double, d: Double) {
    g.color = Color.WHITE
    g.font = g.font.deriveFont(java.awt.Font.BOLD, (d * BADGE_TEXT_RATIO).toFloat())
    val metrics = g.fontMetrics
    val x = left + (d - metrics.stringWidth(text)) / 2
    val y = (d - metrics.height) / 2 + metrics.ascent
    g.drawString(text, x.toFloat(), y.toFloat())
}
