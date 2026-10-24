package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection

/** The display half of the emulator: remembers the cursor and the modes the application switched on, and reports every change. */
internal class ScreenModes(private val onChange: () -> Unit) : TerminalDisplay {
    @Volatile
    var cursorColumn = 0
        private set

    @Volatile
    var cursorRow = 0
        private set

    @Volatile
    var cursorShape: CursorShape? = null
        private set

    @Volatile
    var cursorVisible = true
        private set

    @Volatile
    var mouseMode = MouseMode.MOUSE_REPORTING_NONE
        private set

    @Volatile
    var mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM
        private set

    @Volatile
    var bracketedPaste = false
        private set

    @Volatile
    var alternateScreen = false
        private set

    @Volatile
    private var title = ""

    override fun setCursor(x: Int, y: Int) {
        cursorColumn = x
        cursorRow = y - 1
        onChange()
    }

    override fun setCursorShape(cursorShape: CursorShape?) {
        this.cursorShape = cursorShape
        onChange()
    }

    override fun setCursorVisible(isCursorVisible: Boolean) {
        cursorVisible = isCursorVisible
        onChange()
    }

    override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {
        alternateScreen = useAlternateScreenBuffer
        onChange()
    }

    override fun terminalMouseModeSet(mouseMode: MouseMode) {
        this.mouseMode = mouseMode
    }

    override fun setMouseFormat(mouseFormat: MouseFormat) {
        this.mouseFormat = mouseFormat
    }

    override fun setBracketedPasteMode(bracketedPasteModeEnabled: Boolean) {
        bracketedPaste = bracketedPasteModeEnabled
    }

    override fun beep() = Unit

    override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) = Unit

    override fun getWindowTitle(): String = title

    override fun setWindowTitle(windowTitle: String) {
        title = windowTitle
    }

    override fun getSelection(): TerminalSelection? = null

    override fun ambiguousCharsAreDoubleWidth(): Boolean = false
}
