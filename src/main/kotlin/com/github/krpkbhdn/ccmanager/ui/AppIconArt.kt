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
private val sheen = Color(255, 255, 255, 64)

/** App icon drawn in code: a clay-gradient rounded square with a terminal prompt, so no image resource is shipped. */
fun drawAppIcon(size: Int): BufferedImage {
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
    g.dispose()
    return image
}
