package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets

private const val ESCAPE_BYTE: Byte = 0x1B
private const val CTRL_C_BYTE: Byte = 0x03
private const val NANOS_PER_MILLI = 1_000_000L

/** A write of exactly one ESC byte is the Esc key; escape sequences and Alt combos are longer. */
internal fun isLoneEscape(bytes: ByteArray): Boolean = bytes.size == 1 && bytes[0] == ESCAPE_BYTE

/** Connects JediTerm to a pty4j process; reports a lone Esc key press through [onEscape]. */
internal class PtyTtyConnector(
    private val ptyProcess: PtyProcess,
    private val displayName: String,
    private val onEscape: () -> Unit,
) : ProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8) {

    override fun getName(): String = displayName

    override fun resize(termSize: TermSize) {
        if (ptyProcess.isAlive) {
            ptyProcess.winSize = WinSize(termSize.columns, termSize.rows)
        }
    }

    @Volatile
    private var lastCtrlCNanos = 0L

    /** True when a Ctrl+C was written less than [windowMillis] ago; a second one would make claude exit. */
    fun ctrlCWithin(windowMillis: Long): Boolean =
        lastCtrlCNanos != 0L && System.nanoTime() - lastCtrlCNanos < windowMillis * NANOS_PER_MILLI

    override fun write(bytes: ByteArray) {
        if (isLoneEscape(bytes)) onEscape()
        if (bytes.size == 1 && bytes[0] == CTRL_C_BYTE) lastCtrlCNanos = System.nanoTime()
        super.write(bytes)
    }
}
