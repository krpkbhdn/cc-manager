package com.github.krpkbhdn.ccmanager.ui.terminal

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Rectangle
import java.awt.event.AdjustmentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.plaf.basic.BasicScrollBarUI

private const val BAR_WIDTH = 10
private const val THUMB_INSET = 2
private const val IDLE_HIDE_MILLIS = 1200L
private const val FRAME_MILLIS = 30
private const val FADE_STEP = 0.15f
private const val THUMB_MAX_ALPHA = 0.45f
private const val THUMB_ACTIVE_ALPHA = 0.7f

/** Thin rounded scrollbar without arrow buttons that fades out while the terminal is idle. */
internal class AutoHideScrollBarUI(
    private val trackColor: () -> Color,
    private val thumbColor: () -> Color,
) : BasicScrollBarUI() {
    private var opacity = 0f
    private var lastActivityMillis = 0L
    private var hovered = false
    private val fadeTimer = Timer(FRAME_MILLIS) { fadeStep() }
    private val activity = AdjustmentListener { wake() }
    private val hover = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) { hovered = true; wake() }
        override fun mouseExited(e: MouseEvent) { hovered = false; wake() }
    }

    override fun installListeners() {
        super.installListeners()
        scrollbar.addAdjustmentListener(activity)
        scrollbar.addMouseListener(hover)
    }

    override fun uninstallListeners() {
        fadeTimer.stop()
        scrollbar.removeAdjustmentListener(activity)
        scrollbar.removeMouseListener(hover)
        super.uninstallListeners()
    }

    override fun getPreferredSize(c: JComponent): Dimension = Dimension(BAR_WIDTH, BAR_WIDTH)

    override fun createDecreaseButton(orientation: Int): JButton = emptyButton()

    override fun createIncreaseButton(orientation: Int): JButton = emptyButton()

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
        g.color = trackColor()
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height)
    }

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
        if (opacity <= 0f || thumbBounds.isEmpty) return
        val peak = if (isDragging || isThumbRollover) THUMB_ACTIVE_ALPHA else THUMB_MAX_ALPHA
        val color = thumbColor()
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color(color.red, color.green, color.blue, (255 * peak * opacity).toInt().coerceIn(0, 255))
            val width = thumbBounds.width - THUMB_INSET * 2
            g2.fillRoundRect(thumbBounds.x + THUMB_INSET, thumbBounds.y, width, thumbBounds.height, width, width)
        } finally {
            g2.dispose()
        }
    }

    private fun wake() {
        lastActivityMillis = System.currentTimeMillis()
        if (!fadeTimer.isRunning) fadeTimer.start()
    }

    private fun fadeStep() {
        val visible = hovered || isDragging || System.currentTimeMillis() - lastActivityMillis < IDLE_HIDE_MILLIS
        opacity = if (visible) minOf(1f, opacity + FADE_STEP) else maxOf(0f, opacity - FADE_STEP)
        scrollbar.repaint()
        if (!visible && opacity == 0f) fadeTimer.stop()
    }

    private fun emptyButton() = JButton().apply { preferredSize = Dimension(0, 0); isVisible = false }
}
