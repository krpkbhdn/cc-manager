package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SUBMIT_DELAY_MILLIS = 700L
private const val EXIT_POLL_MILLIS = 200L
private const val EXIT_TIMEOUT_MILLIS = 15_000L
private const val EXIT_COMMAND = "/exit"
private const val ENTER = "\r"

/**
 * Switches a running session's model by exiting claude cleanly (it only writes the transcript on exit) and resuming the
 * conversation with --model/--effort, which unlike typing /model leaves the user's saved Claude defaults alone.
 */
class SessionRestarter(private val backend: Backend, private val isRunning: (String) -> Boolean) {

    /** @throws IllegalStateException with a user-readable message when claude does not exit or cannot resume */
    suspend fun restart(session: SessionSnapshot, selection: ModelSelection): LaunchedSession {
        val id = session.ccmSessionId
        backend.terminal.send(id, EXIT_COMMAND)
        delay(SUBMIT_DELAY_MILLIS)
        backend.terminal.send(id, ENTER)
        if (!awaitExit(id)) throw IllegalStateException("Claude did not exit; the session was left as it is.")
        return withContext(Dispatchers.IO) {
            backend.terminal.close(id)
            backend.launcher.restart(session.projectId, session.claudeSessionId, selection)
        }
    }

    private suspend fun awaitExit(id: String): Boolean {
        var waited = 0L
        while (isRunning(id) && waited < EXIT_TIMEOUT_MILLIS) {
            delay(EXIT_POLL_MILLIS)
            waited += EXIT_POLL_MILLIS
        }
        return !isRunning(id)
    }
}
