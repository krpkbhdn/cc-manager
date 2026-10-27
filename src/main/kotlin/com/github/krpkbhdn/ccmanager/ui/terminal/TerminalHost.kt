package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.pty4j.PtyProcess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

private class OpenTerminal(val session: TerminalSession, val process: PtyProcess, val connector: PtyTtyConnector, val isClaude: Boolean)

/**
 * Owns one [TerminalSession] per opened session so tabs survive navigation. The UI calls [configure] once at
 * startup so that a lone Esc press can be reported to the registry.
 */
object TerminalHost {
    private val terminals = ConcurrentHashMap<String, OpenTerminal>()
    private val pendingClose = CopyOnWriteArrayList<Thread>()

    @Volatile
    private var registry: SessionRegistry? = null

    /** Gives the host the registry that learns about Esc interrupts; call once before [open]. */
    fun configure(sessionRegistry: SessionRegistry) {
        registry = sessionRegistry
    }

    fun open(session: LaunchedSession) = open(session.ccmSessionId(), session.label(), session.process(), isClaude = true)

    /** Plain shells are opened under their own ids; the registry ignores Esc reports for ids it does not know. */
    fun open(shell: LaunchedShell) = open(shell.terminalId(), shell.shell().name(), shell.process(), isClaude = false)

    private fun open(id: String, label: String, process: PtyProcess, isClaude: Boolean) {
        terminals.computeIfAbsent(id) { createTerminal(id, label, process, isClaude) }
    }

    /** Types text into the session console exactly as if the user typed it (CR = Enter, ESC = Esc). */
    fun send(ccmSessionId: String, text: String) {
        val terminal = terminals[ccmSessionId] ?: return
        terminal.session.send(text)
    }

    fun ctrlCWithin(ccmSessionId: String, windowMillis: Long): Boolean =
        terminals[ccmSessionId]?.connector?.ctrlCWithin(windowMillis) == true

    fun enterWithin(ccmSessionId: String, windowMillis: Long): Boolean =
        terminals[ccmSessionId]?.session?.enterWithin(windowMillis) == true

    fun draftMayExist(ccmSessionId: String): Boolean =
        terminals[ccmSessionId]?.session?.draftMayExist == true

    /** Moves the keyboard focus into the terminal when it is on screen. */
    fun focus(ccmSessionId: String) {
        terminals[ccmSessionId]?.let { runCatching { it.session.focusRequester.requestFocus() } }
    }

    /** Removes the terminal at once; claude is asked to exit and killed if it stays up, shells are killed at once, off the calling thread. */
    fun close(ccmSessionId: String) = synchronized(this) {
        val terminal = detach(ccmSessionId) ?: return@synchronized
        val ending = thread(start = false, isDaemon = true, name = "TerminalClose-$ccmSessionId") {
            end(listOf(terminal))
            pendingClose.remove(Thread.currentThread())
        }
        pendingClose.add(ending)
        ending.start()
    }

    /** Ends every session in parallel within one grace period; processes never outlive the app. */
    fun closeAll() {
        val detached = synchronized(this) { terminals.keys.toList().mapNotNull(::detach) }
        end(detached)
        pendingClose.toList().forEach(Thread::join)
    }

    private fun detach(ccmSessionId: String): OpenTerminal? = terminals.remove(ccmSessionId)?.also { registry?.discard(ccmSessionId) }

    internal fun sessionOf(ccmSessionId: String): TerminalSession? = terminals[ccmSessionId]?.session

    internal fun anyFocused(): Boolean = terminals.values.any { it.session.focused }

    private fun end(ending: List<OpenTerminal>) {
        val (claude, others) = ending.filter { it.process.isAlive }.partition { it.isClaude }
        killUnlessExited(others.map { it.process }, 0)
        claude.map { thread(isDaemon = true) { requestExit(it.session) } }.forEach(Thread::join)
        killUnlessExited(claude.map { it.process }, EXIT_GRACE_MILLIS)
        ending.forEach { it.session.close() }
    }

    private fun createTerminal(id: String, label: String, process: PtyProcess, isClaude: Boolean): OpenTerminal {
        val connector = PtyTtyConnector(process, label) { registry?.markInterrupted(id) }
        return OpenTerminal(TerminalSession(connector), process, connector, isClaude)
    }
}
