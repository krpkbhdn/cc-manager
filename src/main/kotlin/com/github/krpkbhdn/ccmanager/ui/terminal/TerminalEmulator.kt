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
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import java.io.IOException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.slf4j.LoggerFactory

internal const val SCROLLBACK_LINES = 10_000
private const val FRAME_MILLIS = 16L
private const val CONNECTOR_RESIZE_MILLIS = 100L
private const val WORKER_IDLE_SECONDS = 2L
private const val EXIT_WAIT_MILLIS = 2_000L
private const val EXIT_POLL_MILLIS = 20L

private val LOG = LoggerFactory.getLogger(TerminalEmulator::class.java)

/** Writes queued behind a child that does not read stdin; past this they are dropped instead of replayed as a burst. */
private const val MAX_PENDING_WRITES = 1024

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
    scrollbackLines: Int = SCROLLBACK_LINES,
) {
    // the idle worker thread exits (an ended session keeps its tab) and is started again on demand
    private val worker = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "TerminalWorker-${connector.name}").apply { isDaemon = true } }
        .apply { setKeepAliveTime(WORKER_IDLE_SECONDS, TimeUnit.SECONDS); allowCoreThreadTimeOut(true) }
    private val framePending = AtomicBoolean(false)
    private val discardedLines = AtomicInteger()
    private var connectorResize: ScheduledFuture<*>? = null

    // input goes through its own thread: a write blocks while the process does not drain stdin and must not stall frames or resizes
    private val writer = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "TerminalWriter-${connector.name}").apply { isDaemon = true } }
        .apply { setKeepAliveTime(WORKER_IDLE_SECONDS, TimeUnit.SECONDS); allowCoreThreadTimeOut(true) }

    private val pendingWrites = AtomicInteger()

    val modes = ScreenModes(::invalidate)
    val buffer: TerminalTextBuffer
    val terminal: JediTerminal

    init {
        val styleState = StyleState().apply { setDefaultStyle(defaultStyle) }
        buffer = TerminalTextBuffer(columns, rows, styleState, scrollbackLines)
        terminal = JediTerminal(modes, buffer, styleState)
        terminal.setTerminalOutput(output())
        buffer.addModelListener(::invalidate)
        buffer.addChangesListener(object : TextBufferChangesListener {
            override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
                discardedLines.addAndGet(lines.size)
            }
        })
    }

    /** Lines the full scrollback dropped from its start since the last call; the history count alone does not show them. */
    fun takeDiscardedLines(): Int = discardedLines.getAndSet(0)

    fun start() {
        thread(name = "TerminalEmulator-${connector.name}", isDaemon = true) { readOutput() }
    }

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (pendingWrites.incrementAndGet() > MAX_PENDING_WRITES) {
            pendingWrites.decrementAndGet()
            return
        }
        val queued = runCatching { writer.execute { try { writeNow(bytes) } finally { pendingWrites.decrementAndGet() } } }
        if (queued.isFailure) pendingWrites.decrementAndGet()
    }

    fun resize(columns: Int, rows: Int) {
        runCatching {
            worker.execute {
                terminal.resize(TermSize(columns, rows), RequestOrigin.User)
                connectorResize?.cancel(false)
                connectorResize = writer.schedule({ connector.resize(TermSize(columns, rows)) }, CONNECTOR_RESIZE_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun close() {
        worker.shutdownNow()
        writer.shutdownNow()
        runCatching { connector.close() }
    }

    private fun readOutput() {
        val dataStream = TtyBasedArrayDataStream(connector, null)
        val emulator = JediEmulator(dataStream, terminal)
        try {
            var reading = true
            while (reading) {
                try {
                    while (emulator.hasNext()) emulator.next()
                    reading = false
                } catch (_: IOException) {
                    reading = false
                } catch (e: RuntimeException) {
                    LOG.warn("Terminal output of {} failed to parse: {}: {}", connector.name, e.javaClass.name, e.message)
                    reading = connector.isConnected
                }
            }
        } finally {
            terminal.disconnected()
            announceEnd()
        }
    }

    /** The stream normally ends with the process; one that closed its side but stays alive keeps its connector. */
    private fun announceEnd() {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(EXIT_WAIT_MILLIS)
        while (connector.isConnected && System.nanoTime() < deadline) Thread.sleep(EXIT_POLL_MILLIS)
        val dead = !connector.isConnected
        val exitCode = if (dead) runCatching { connector.waitFor() }.getOrDefault(-1) else null
        terminal.carriageReturn()
        terminal.nextLine()
        terminal.writeCharacters(if (exitCode != null) "[Session ended (exit code $exitCode)]" else "[Output closed]")
        terminal.nextLine()
        if (dead) runCatching { connector.close() }
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
