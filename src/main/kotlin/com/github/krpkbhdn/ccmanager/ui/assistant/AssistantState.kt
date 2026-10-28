package com.github.krpkbhdn.ccmanager.ui.assistant

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MILLIS = 300L
private const val WATCH_MILLIS = 15_000L
private const val TRUST_YES_KEYS = "\u001b[B\r"

/** Claude's folder-trust dialog; matched loosely because its wording differs between versions. */
fun isTrustDialog(screen: String): Boolean = screen.contains("trust", ignoreCase = true) && screen.contains("folder", ignoreCase = true)

/** The assistant session outside any project: starting or focusing it and answering Claude's trust question for its folder. */
class AssistantState(
    private val scope: CoroutineScope,
    private val terminal: TerminalPort,
    private val live: () -> LiveSessions,
    private val folder: Path,
    private val launch: () -> Unit,
    private val show: () -> Unit,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val pollMillis: Long = POLL_MILLIS,
    private val watchMillis: Long = WATCH_MILLIS,
) {
    val liveSessionId: String?
        get() = live().of(ClaudeLauncher.ASSISTANT_PROJECT_ID).firstOrNull()?.ccmSessionId

    fun start() {
        val running = liveSessionId
        if (running != null) {
            show()
            terminal.focus(running)
            return
        }
        launch()
        scope.launch { watchTrustDialog() }
    }

    /** Claude shows the dialog before SessionStart, so it ends once the session reports its claude id. */
    private suspend fun watchTrustDialog() {
        var waited = 0L
        while (waited <= watchMillis) {
            val session = live().of(ClaudeLauncher.ASSISTANT_PROJECT_ID).firstOrNull()
            if (session?.claudeSessionId != null) return
            if (session != null && isTrustDialog(terminal.screenText(session.ccmSessionId))) return askTrust(session.ccmSessionId)
            delay(pollMillis)
            waited += pollMillis
        }
    }

    private fun askTrust(ccmSessionId: String) = requestConfirm(
        ConfirmRequest(
            title = "Trust folder",
            message = "Trust the assistant folder $folder?",
            confirmLabel = "Yes",
            destructive = false,
        ) { terminal.send(ccmSessionId, TRUST_YES_KEYS) },
    )
}
