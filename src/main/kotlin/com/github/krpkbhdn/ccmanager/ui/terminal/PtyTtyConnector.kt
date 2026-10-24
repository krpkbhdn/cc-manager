package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets

private const val ESCAPE_BYTE: Byte = 0x1B

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

    override fun write(bytes: ByteArray) {
        if (isLoneEscape(bytes)) onEscape()
        super.write(bytes)
    }
}
