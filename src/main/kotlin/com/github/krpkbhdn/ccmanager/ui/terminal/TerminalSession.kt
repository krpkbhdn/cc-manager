package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jediterm.core.input.KeyInputEvent
import com.jediterm.core.input.MouseEvent
import com.jediterm.core.input.MouseWheelEvent
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings
import com.jediterm.terminal.emulator.mouse.MouseMode
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val INITIAL_COLUMNS = 120
private const val INITIAL_ROWS = 32
internal const val DEFAULT_CELL_DP = 8
private const val MIN_CELL_DP = 6
private const val MAX_CELL_DP = 20
private const val ENTER_BYTE: Byte = 0x0D
private const val NANOS_PER_MILLI = 1_000_000L

private fun Color.toJediColor() = com.jediterm.core.Color(toArgb() and 0xFFFFFF)

/**
 * One terminal as the UI sees it. Everything that must survive navigation lives here (emulator, scrollback,
 * selection, zoom); the composable only draws it. [frame] changes whenever the screen has to be redrawn.
 */
internal class TerminalSession(connector: TtyConnector, scrollbackCap: Int = SCROLLBACK_LINES) {
    private val frameState = mutableLongStateOf(0)
    private var knownHistory = 0
    private val pendingGrowth = AtomicInteger()
    private val historyShrank = AtomicBoolean()
    private var suppressTyped = false
    private var highSurrogate: Char? = null

    var theme by mutableStateOf(TerminalTheme())
    var selection by mutableStateOf<CellSelection?>(null)
        private set
    var cellDp by mutableIntStateOf(DEFAULT_CELL_DP)
        private set
    var scrollTarget by mutableFloatStateOf(0f)
        private set
    var scrollShown by mutableFloatStateOf(0f)
        private set
    var focused by mutableStateOf(false)

    val focusRequester = FocusRequester()
    val frame: Long get() = frameState.longValue

    private val emulator = TerminalEmulator(connector, INITIAL_COLUMNS, INITIAL_ROWS, defaultStyle(), ::onEmulatorFrame, scrollbackCap)

    val buffer get() = emulator.buffer
    val modes get() = emulator.modes
    val columns: Int get() = buffer.width
    val rows: Int get() = buffer.height

    /** Lines of scrollback that can be scrolled to; the alternate screen has none. */
    val scrollbackLines: Int get() = if (modes.alternateScreen) 0 else buffer.historyLinesCount

    val mouseReporting: Boolean get() = modes.mouseMode != MouseMode.MOUSE_REPORTING_NONE

    init {
        emulator.start()
    }

    private fun defaultStyle() = TextStyle(
        TerminalColor { theme.foreground.toJediColor() },
        TerminalColor { theme.background.toJediColor() },
    )

    private fun onEmulatorFrame() {
        val history = scrollbackLines
        if (history < knownHistory) historyShrank.set(true)
        pendingGrowth.addAndGet(history - knownHistory + emulator.takeDiscardedLines())
        knownHistory = history
        frameState.longValue++
    }

    /** Moves scroll position and selection with the content; runs on the UI thread, which owns that state. */
    fun syncView() {
        if (historyShrank.getAndSet(false)) selection = null
        val grown = pendingGrowth.getAndSet(0)
        if (grown != 0) keepViewOnContent(grown)
    }

    private fun keepViewOnContent(grown: Int) {
        if (grown < 0) {
            selection = null
        } else {
            selection = selection?.let { CellSelection(it.anchor.copy(line = it.anchor.line - grown), it.head.copy(line = it.head.line - grown)) }
            if (scrollTarget > 0f) shiftScroll(grown.toFloat())
        }
        scrollTarget = scrollTarget.coerceIn(0f, scrollbackLines.toFloat())
        scrollShown = scrollShown.coerceIn(0f, scrollbackLines.toFloat())
    }

    private fun shiftScroll(lines: Float) {
        scrollTarget += lines
        scrollShown += lines
    }

    fun send(text: String) = emulator.write(text.toByteArray(Charsets.UTF_8))

    @Volatile
    private var lastEnterNanos = 0L

    /** True when the user pressed Enter less than [windowMillis] ago: the turn it starts is not WORKING yet and Ctrl+C would abort it. */
    fun enterWithin(windowMillis: Long): Boolean =
        lastEnterNanos != 0L && System.nanoTime() - lastEnterNanos < windowMillis * NANOS_PER_MILLI

    /** Handles a key event; returns true when it was consumed here. */
    fun onKey(event: KeyInputEvent): Boolean {
        if (event.type == KeyInputEvent.Type.TYPED && suppressTyped && event.modifiersEx != 0) return true
        val command = commandFor(event, selection != null)
        if (command != null && perform(command)) {
            suppressTyped = true
            return true
        }
        if (event.type == KeyInputEvent.Type.PRESSED) suppressTyped = false
        val bytes = typedPair(event) ?: optionLetterBytes(event)?.also { suppressTyped = true } ?: keyBytes(event, emulator.terminal) ?: return false
        selection = null
        scrollToBottom()
        if (bytes.size == 1 && bytes[0] == ENTER_BYTE) lastEnterNanos = System.nanoTime()
        emulator.write(bytes)
        return true
    }

    /** Characters outside the BMP arrive as two typed surrogates; they must reach the process as one UTF-8 sequence. */
    private fun typedPair(event: KeyInputEvent): ByteArray? {
        if (event.type != KeyInputEvent.Type.TYPED) return null
        val char = event.keyChar
        if (char.isHighSurrogate()) {
            highSurrogate = char
            return ByteArray(0)
        }
        val high = highSurrogate.takeIf { char.isLowSurrogate() } ?: return null
        highSurrogate = null
        return String(charArrayOf(high, char)).toByteArray(Charsets.UTF_8)
    }

    /** False when the key should still reach the process: Ctrl+V with no text on the clipboard is an app's image paste. */
    private fun perform(command: TerminalCommand): Boolean {
        when (command) {
            is TerminalCommand.Zoom -> zoomBy(command.steps)
            TerminalCommand.ResetZoom -> cellDp = DEFAULT_CELL_DP
            TerminalCommand.Copy -> copySelection()
            TerminalCommand.Paste -> return paste()
        }
        return true
    }

    fun zoomBy(steps: Int) {
        cellDp = (cellDp + steps).coerceIn(MIN_CELL_DP, MAX_CELL_DP)
    }

    fun copySelection() {
        val text = selection?.textIn(buffer)?.takeIf { it.isNotEmpty() } ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        selection = null
    }

    /** Pastes the clipboard text; false when it holds none. */
    fun paste(): Boolean {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val text = runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull().orEmpty()
        if (text.isEmpty()) return false
        scrollToBottom()
        emulator.write(pasteBytes(text, modes.bracketedPaste))
        return true
    }

    fun selectAll() {
        selection = CellSelection(Cell(0, -scrollbackLines), Cell(columns - 1, rows - 1))
    }

    fun clearSelection() {
        selection = null
    }

    fun select(newSelection: CellSelection?) {
        selection = newSelection
    }

    fun lineText(line: Int): String = buffer.withLock { buffer.getLine(line.coerceIn(-scrollbackLines, rows - 1)).text }

    fun scrollBy(lines: Float) {
        scrollTarget = (scrollTarget + lines).coerceIn(0f, scrollbackLines.toFloat())
    }

    fun scrollJumpTo(lines: Float) {
        scrollTarget = lines.coerceIn(0f, scrollbackLines.toFloat())
        scrollShown = scrollTarget
    }

    fun scrollToBottom() = scrollJumpTo(0f)

    fun showScrollAt(shown: Float) {
        scrollShown = shown
    }

    fun fit(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return
        selection = null
        emulator.resize(newColumns, newRows)
    }

    fun reportMouse(event: MouseEvent, column: Int, row: Int): Boolean {
        val settings = MouseEventProcessingSettings(mouseReporting, modes.alternateScreen, true)
        return emulator.terminal.onMouseEvent(column, row, event, settings)
    }

    fun reportWheel(buttonCode: Int, modifierKeys: Int, lines: Int, column: Int, row: Int): Boolean =
        reportMouse(MouseWheelEvent(buttonCode, modifierKeys, lines), column, row)

    fun screenText(): String = buffer.withLock { buffer.getScreenLines() }

    fun close() = emulator.close()
}
