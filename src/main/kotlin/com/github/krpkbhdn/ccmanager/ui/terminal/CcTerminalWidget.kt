package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.TerminalPanel
import com.jediterm.terminal.ui.settings.SettingsProvider
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JScrollBar

private const val HORIZONTAL_PADDING = 12
private val ZOOM_STEP = fontSizeForCellWidth(1)

/** JediTerm panel that picks a font per glyph, renders crisp text, pads the left edge and zooms with Ctrl +/-/0. */
internal class CcTerminalPanel(
    private val settings: TerminalSettings,
    buffer: TerminalTextBuffer,
    styleState: StyleState,
) : TerminalPanel(settings, buffer, styleState) {

    override fun getFontToDisplay(text: CharArray, start: Int, end: Int, style: TextStyle): Font {
        var awtStyle = Font.PLAIN
        if (style.hasOption(TextStyle.Option.BOLD)) awtStyle = awtStyle or Font.BOLD
        if (style.hasOption(TextStyle.Option.ITALIC)) awtStyle = awtStyle or Font.ITALIC
        return settings.fonts.fontFor(Character.codePointAt(text, start, end), awtStyle)
    }

    override fun setupAntialiasing(graphics: Graphics) {
        val gfx = graphics as? Graphics2D ?: return
        gfx.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        gfx.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        gfx.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        gfx.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    override fun getInsetX(): Int = HORIZONTAL_PADDING

    override fun handleKeyEvent(e: KeyEvent) {
        val step = zoomStep(e)
        if (step == null) {
            super.handleKeyEvent(e)
            return
        }
        e.consume()
        if (e.id == KeyEvent.KEY_PRESSED) zoom(step)
    }

    private fun zoom(step: Float) {
        settings.fontSize = if (step == 0f) DEFAULT_FONT_SIZE else settings.fontSize + step
        reinitFontAndResize()
        repaint()
    }

    private fun zoomStep(e: KeyEvent): Float? {
        val ctrlOnly = e.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK) ==
            InputEvent.CTRL_DOWN_MASK
        if (!ctrlOnly) return null
        return when (e.keyCode) {
            KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> ZOOM_STEP
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> if (e.isShiftDown) null else -ZOOM_STEP
            KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> 0f
            else -> null
        }
    }
}

/** Terminal widget with [CcTerminalPanel] and a thin auto-hiding scrollbar that follows the terminal colors. */
internal class CcTerminalWidget(columns: Int, rows: Int, settings: TerminalSettings) :
    JediTermWidget(columns, rows, settings) {

    override fun createTerminalPanel(
        settingsProvider: SettingsProvider,
        styleState: StyleState,
        terminalTextBuffer: TerminalTextBuffer,
    ): TerminalPanel = CcTerminalPanel(settingsProvider as TerminalSettings, terminalTextBuffer, styleState)

    override fun createScrollBar(): JScrollBar {
        val settings = mySettingsProvider as TerminalSettings
        return JScrollBar().apply { ui = AutoHideScrollBarUI({ settings.background }, { settings.foreground }) }
    }
}
