package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

private const val SUBMIT_DELAY_MILLIS = 700L
private const val ENTER = "\r"
private const val CLEAR_INPUT = "\u0003"
private const val ALT_P = "\u001bp"
private const val EXIT_HINT_MILLIS = 2_000L
private const val SUBMITTED_TURN_MILLIS = 1_500L
const val STARTING_MESSAGE = "Claude is still starting. Wait until it is ready."

/**
 * Types slash commands into a session's PTY. Text and Enter must be separate writes: claude takes them in one write as a
 * paste and does not submit. [pause] is injectable so tests need no real waiting.
 */
class SessionCommandWriter(private val terminal: TerminalPort, private val pause: suspend (Long) -> Unit = { delay(it) }) {

    private val sending = ConcurrentHashMap.newKeySet<String>()

    /**
     * Clears the whole typed draft (Ctrl+C: Ctrl+U only clears one wrapped row), its own write, so the command is not glued onto it.
     * Only for an idle session, Ctrl+C interrupts a turn. Returns false without typing when the session is still being sent to
     * or the user pressed Ctrl+C a moment ago (a second one would exit claude).
     */
    suspend fun run(ccmSessionId: String, command: String): Boolean {
        if (!sending.add(ccmSessionId)) return false
        try {
            if (terminal.ctrlCWithin(ccmSessionId, EXIT_HINT_MILLIS) || terminal.enterWithin(ccmSessionId, SUBMITTED_TURN_MILLIS)) return false
            terminal.send(ccmSessionId, CLEAR_INPUT)
            pause(SUBMIT_DELAY_MILLIS)
            terminal.send(ccmSessionId, command)
            pause(SUBMIT_DELAY_MILLIS)
            terminal.send(ccmSessionId, ENTER)
            return true
        } finally {
            sending.remove(ccmSessionId)
        }
    }

    /** Alt+P (Meta+p), claude's own model switcher, which then runs inside the terminal. */
    fun openModelPicker(ccmSessionId: String) = terminal.send(ccmSessionId, ALT_P)
}

/** Why a command must not be typed into [session] now, null when it may: Ctrl+C would interrupt a turn or reject a prompt, and before SessionStart claude may show a dialog or not read the keyboard yet. An [attached] background agent is past those dialogs and may never report to our hooks, so it skips that wait. */
fun commandBlockedReason(session: SessionSnapshot?, attached: Boolean = false): String? = when {
    session?.status == AgentStatus.WORKING -> "Claude is working. Send the command when the session is free."
    session?.status == AgentStatus.NEEDS_ACTION -> "Claude is waiting for you. Answer first, then send the command."
    session?.status != AgentStatus.IDLE -> "The session is not running."
    session.claudeSessionId == null && !attached ->STARTING_MESSAGE
    else -> null
}
