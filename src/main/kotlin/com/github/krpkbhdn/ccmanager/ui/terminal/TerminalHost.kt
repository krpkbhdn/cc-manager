package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.jediterm.terminal.ui.JediTermWidget
import com.pty4j.PtyProcess
import com.jediterm.terminal.ui.TerminalWidget
import com.jediterm.terminal.ui.TerminalWidgetListener
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

private const val INITIAL_COLUMNS = 120
private const val INITIAL_ROWS = 32

/** A live terminal: the persistent widget plus what is needed to type into it and to stop it. */
internal class OpenTerminal(
    val widget: JediTermWidget,
    val settings: TerminalSettings,
    val session: LaunchedSession,
)

/**
 * Owns one terminal widget per session so tabs survive navigation. The UI calls [configure] once at startup so
 * that a lone Esc press can be reported to the registry.
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
        val connector = terminals[ccmSessionId]?.widget?.ttyConnector ?: return
        connector.write(text.toByteArray(Charsets.UTF_8))
    }

    /** Disposes the widget and kills the process if still running. */
    fun close(ccmSessionId: String) {
        val terminal = terminals.remove(ccmSessionId) ?: return
        killProcessTree(terminal.session.process())
        terminal.widget.close()
    }

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

    internal fun widgetOf(ccmSessionId: String): OpenTerminal? = terminals[ccmSessionId]

    private fun createTerminal(session: LaunchedSession): OpenTerminal {
        val settings = TerminalSettings(dark = true)
        val widget = JediTermWidget(INITIAL_COLUMNS, INITIAL_ROWS, settings)
        val id = session.ccmSessionId()
        widget.addListener(exitNotice(session))
        widget.ttyConnector = PtyTtyConnector(session.process(), session.label()) {
            registry?.markInterrupted(id)
        }
        widget.start()
        return OpenTerminal(widget, settings, session)
    }

    private fun exitNotice(session: LaunchedSession) = object : TerminalWidgetListener {
        override fun allSessionsClosed(widget: TerminalWidget) {
            val exitCode = runCatching { session.process().waitFor() }.getOrDefault(-1)
            SwingUtilities.invokeLater {
                val terminal = (widget as JediTermWidget).terminal
                terminal.carriageReturn()
                terminal.nextLine()
                terminal.writeCharacters("[Session ended (exit code $exitCode)]")
                terminal.nextLine()
            }
        }
    }
}
