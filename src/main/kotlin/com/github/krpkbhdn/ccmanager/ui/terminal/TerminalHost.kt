package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.pty4j.PtyProcess
import java.util.concurrent.ConcurrentHashMap

private class OpenTerminal(val session: TerminalSession, val process: PtyProcess)

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

    fun open(session: LaunchedSession) {
        terminals.computeIfAbsent(session.ccmSessionId()) { createTerminal(session) }
    }

    fun isOpen(ccmSessionId: String): Boolean = terminals.containsKey(ccmSessionId)

    /** Types text into the session console exactly as if the user typed it (CR = Enter, ESC = Esc). */
    fun send(ccmSessionId: String, text: String) {
        terminals[ccmSessionId]?.session?.send(text)
    }

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

    internal fun sessionOf(ccmSessionId: String): TerminalSession? = terminals[ccmSessionId]?.session

    internal fun anyFocused(): Boolean = terminals.values.any { it.session.focused }

    private fun killProcessTree(process: PtyProcess) {
        if (!process.isAlive) return
        if (System.getProperty("os.name").startsWith("Windows")) {
            // pty4j's Windows process has no ProcessHandle, so descendants() is unsupported
            runCatching { ProcessBuilder("taskkill", "/PID", process.pid().toString(), "/T", "/F").start().waitFor() }
        } else {
            process.descendants().forEach { it.destroyForcibly() }
        }
        process.destroyForcibly()
    }

    private fun createTerminal(session: LaunchedSession): OpenTerminal {
        val id = session.ccmSessionId()
        val connector = PtyTtyConnector(session.process(), session.label()) { registry?.markInterrupted(id) }
        return OpenTerminal(TerminalSession(connector), session.process())
    }
}
