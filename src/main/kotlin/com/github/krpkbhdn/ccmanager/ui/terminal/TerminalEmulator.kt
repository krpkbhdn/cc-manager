package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalOutputStream
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val SCROLLBACK_LINES = 10_000
private const val FRAME_MILLIS = 16L
private const val CONNECTOR_RESIZE_MILLIS = 100L

/**
 * The terminal emulation of one process: reads its output on its own thread into [buffer], writes user input and
 * resizes on a single worker, and calls [onFrame] at most every [FRAME_MILLIS] ms while the screen changes.
 */
internal class TerminalEmulator(
    private val connector: TtyConnector,
    columns: Int,
    rows: Int,
    defaultStyle: TextStyle,
    private val onFrame: () -> Unit,
) {
    private val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "TerminalWorker-${connector.name}").apply { isDaemon = true } }
    private val framePending = AtomicBoolean(false)
    private var connectorResize: ScheduledFuture<*>? = null

    val modes = ScreenModes(::invalidate)
    val buffer: TerminalTextBuffer
    val terminal: JediTerminal

    init {
        val styleState = StyleState().apply { setDefaultStyle(defaultStyle) }
        buffer = TerminalTextBuffer(columns, rows, styleState, SCROLLBACK_LINES)
        terminal = JediTerminal(modes, buffer, styleState)
        terminal.setTerminalOutput(output())
        buffer.addModelListener(::invalidate)
    }

    fun start() {
        thread(name = "TerminalEmulator-${connector.name}", isDaemon = true) { readOutput() }
    }

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        runCatching { worker.execute { writeNow(bytes) } }
    }

    fun resize(columns: Int, rows: Int) {
        runCatching {
            worker.execute {
                terminal.resize(TermSize(columns, rows), RequestOrigin.User)
                connectorResize?.cancel(false)
                connectorResize = worker.schedule({ connector.resize(TermSize(columns, rows)) }, CONNECTOR_RESIZE_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun close() {
        worker.shutdownNow()
        runCatching { connector.close() }
    }

    private fun readOutput() {
        val dataStream = TtyBasedArrayDataStream(connector, null)
        val emulator = JediEmulator(dataStream, terminal)
        try {
            while (emulator.hasNext()) emulator.next()
        } catch (_: Exception) {
            // the stream ends with an exception when the process closes its side
        } finally {
            terminal.disconnected()
            announceEnd()
        }
    }

    private fun announceEnd() {
        val exitCode = runCatching { connector.waitFor() }.getOrDefault(-1)
        terminal.carriageReturn()
        terminal.nextLine()
        terminal.writeCharacters("[Session ended (exit code $exitCode)]")
        terminal.nextLine()
    }

    private fun output() = object : TerminalOutputStream {
        override fun sendBytes(response: ByteArray, userInput: Boolean) = write(response)

        override fun sendString(string: String, userInput: Boolean) = write(string.toByteArray(Charsets.UTF_8))
    }

    private fun writeNow(bytes: ByteArray) {
        try {
            connector.write(bytes)
        } catch (_: IOException) {
            // the process is gone; the end of the output stream reports it
        }
    }

    private fun invalidate() {
        if (framePending.compareAndSet(false, true)) {
            runCatching {
                worker.schedule({ framePending.set(false); onFrame() }, FRAME_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }
}
