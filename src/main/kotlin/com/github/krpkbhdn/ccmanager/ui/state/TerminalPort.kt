package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalView
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** The terminal widget layer, behind an interface so the UI can be previewed without a real PTY. */
interface TerminalPort {
    fun open(session: LaunchedSession)
    fun openShell(shell: LaunchedShell) {}
    fun send(ccmSessionId: String, text: String)
    fun close(ccmSessionId: String)

    /** False when the session has no terminal, so [send] would reach nobody. */
    fun hasTerminal(ccmSessionId: String): Boolean = true

    /** Moves the keyboard focus into the session's terminal, if it has one. */
    fun focus(ccmSessionId: String) {}

    /** True when a Ctrl+C reached the session less than [windowMillis] ago. */
    fun ctrlCWithin(ccmSessionId: String, windowMillis: Long): Boolean = false

    /** True when the user pressed Enter less than [windowMillis] ago, before the hook marks the session WORKING. */
    fun enterWithin(ccmSessionId: String, windowMillis: Long): Boolean = false

    /** True while the user may have an unsent draft: typed or pasted since the last Enter or Ctrl+C. */
    fun draftMayExist(ccmSessionId: String): Boolean = false

    /** The text currently on the session's terminal screen; empty without a terminal. */
    fun screenText(ccmSessionId: String): String = ""

    @Composable
    fun View(ccmSessionId: String, modifier: Modifier)
}

object HostTerminalPort : TerminalPort {
    override fun open(session: LaunchedSession) = TerminalHost.open(session)
    override fun openShell(shell: LaunchedShell) = TerminalHost.open(shell)
    override fun send(ccmSessionId: String, text: String) = TerminalHost.send(ccmSessionId, text)
    override fun close(ccmSessionId: String) = TerminalHost.close(ccmSessionId)
    override fun hasTerminal(ccmSessionId: String) = TerminalHost.hasTerminal(ccmSessionId)
    override fun focus(ccmSessionId: String) = TerminalHost.focus(ccmSessionId)
    override fun ctrlCWithin(ccmSessionId: String, windowMillis: Long) = TerminalHost.ctrlCWithin(ccmSessionId, windowMillis)
    override fun enterWithin(ccmSessionId: String, windowMillis: Long) = TerminalHost.enterWithin(ccmSessionId, windowMillis)
    override fun draftMayExist(ccmSessionId: String) = TerminalHost.draftMayExist(ccmSessionId)
    override fun screenText(ccmSessionId: String) = TerminalHost.sessionOf(ccmSessionId)?.screenText().orEmpty()

    @Composable
    override fun View(ccmSessionId: String, modifier: Modifier) = TerminalView(ccmSessionId, modifier, dark = colors.dark, background = colors.terminal)
}
