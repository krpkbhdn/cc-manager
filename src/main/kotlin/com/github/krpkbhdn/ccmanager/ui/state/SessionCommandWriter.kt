package com.github.krpkbhdn.ccmanager.ui.state

import kotlinx.coroutines.delay

private const val SUBMIT_DELAY_MILLIS = 700L
private const val ENTER = "\r"
private const val ALT_P = "\u001bp"

/**
 * Types slash commands into a session's PTY. Text and Enter must be separate writes: claude takes them in one write as a
 * paste and does not submit. [pause] is injectable so tests need no real waiting.
 */
class SessionCommandWriter(private val terminal: TerminalPort, private val pause: suspend (Long) -> Unit = { delay(it) }) {

    suspend fun run(ccmSessionId: String, command: String) {
        terminal.send(ccmSessionId, command)
        pause(SUBMIT_DELAY_MILLIS)
        terminal.send(ccmSessionId, ENTER)
    }

    /** Alt+P (Meta+p), claude's own model switcher, which then runs inside the terminal. */
    fun openModelPicker(ccmSessionId: String) = terminal.send(ccmSessionId, ALT_P)
}
