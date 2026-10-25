package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree
import com.pty4j.PtyProcess
import java.util.concurrent.ConcurrentHashMap

private class OpenTerminal(val session: TerminalSession, val process: PtyProcess, val connector: PtyTtyConnector)

/**
 * Owns one [TerminalSession] per opened session so tabs survive navigation. The UI calls [configure] once at
 * startup so that a lone Esc press can be reported to the registry.
 */
object TerminalHost {
    private val terminals = ConcurrentHashMap<String, OpenTerminal>()

    @Volatile
    private var registry: SessionRegistry? = null

    /** Gives the host the registry that learns about Esc interrupts; call once before [open]. */
    fun configure(sessionRegistry: SessionRegistry) {
        registry = sessionRegistry
    }

    fun open(session: LaunchedSession) = open(session.ccmSessionId(), session.label(), session.process())

    /** Plain shells are opened under their own ids; the registry ignores Esc reports for ids it does not know. */
    fun open(shell: LaunchedShell) = open(shell.terminalId(), shell.shell().name(), shell.process())

    private fun open(id: String, label: String, process: PtyProcess) {
        terminals.computeIfAbsent(id) { createTerminal(id, label, process) }
    }

    /** Types text into the session console exactly as if the user typed it (CR = Enter, ESC = Esc). */
    fun send(ccmSessionId: String, text: String) {
        val terminal = terminals[ccmSessionId] ?: return
        terminal.session.send(text)
        if (text == "\r") terminal.connector.forgetEnter()
    }

    fun ctrlCWithin(ccmSessionId: String, windowMillis: Long): Boolean =
        terminals[ccmSessionId]?.connector?.ctrlCWithin(windowMillis) == true

    fun enterWithin(ccmSessionId: String, windowMillis: Long): Boolean =
        terminals[ccmSessionId]?.connector?.enterWithin(windowMillis) == true

    /** Moves the keyboard focus into the terminal when it is on screen. */
    fun focus(ccmSessionId: String) {
        terminals[ccmSessionId]?.let { runCatching { it.session.focusRequester.requestFocus() } }
    }

    /** Disposes the terminal and kills the process if still running. */
    fun close(ccmSessionId: String) {
        val terminal = terminals.remove(ccmSessionId) ?: return
        killProcessTree(terminal.process)
        terminal.session.close()
    }

    fun closeAll() = terminals.keys.toList().forEach(::close)

    internal fun sessionOf(ccmSessionId: String): TerminalSession? = terminals[ccmSessionId]?.session

    internal fun anyFocused(): Boolean = terminals.values.any { it.session.focused }

    private fun killProcessTree(process: PtyProcess) {
        if (process.isAlive) ProcessTree.kill(process)
    }

    private fun createTerminal(id: String, label: String, process: PtyProcess): OpenTerminal {
        val connector = PtyTtyConnector(process, label) { registry?.markInterrupted(id) }
        return OpenTerminal(TerminalSession(connector), process, connector)
    }
}
