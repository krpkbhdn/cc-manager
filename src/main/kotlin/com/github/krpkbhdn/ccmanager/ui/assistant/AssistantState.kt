package com.github.krpkbhdn.ccmanager.ui.assistant

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MILLIS = 300L
private const val WATCH_MILLIS = 15_000L
private const val ANSWER_MILLIS = 120_000L
private const val ENTER = "\r"
private const val DOWN_THEN_ENTER = "\u001b[B\r"
private const val YES_OPTION = "Yes, I trust this folder"
private val TRUST_MARKERS = listOf(YES_OPTION, "Do you trust the files")
private val CURSOR_MARKERS = listOf('>', '❯', '›', '→')

/** Claude's folder-trust dialog, found by its option and question lines. */
fun isTrustDialog(screen: String): Boolean = TRUST_MARKERS.any { screen.contains(it, ignoreCase = true) }

/** Enter alone when the cursor is on the Yes line; otherwise Down first, as the dialog may default to "No, exit". */
fun trustYesKeys(screen: String): String {
    val yesLine = screen.lineSequence().firstOrNull { it.contains(YES_OPTION, ignoreCase = true) }
    return if (yesLine != null && yesLine.trimStart().firstOrNull() in CURSOR_MARKERS) ENTER else DOWN_THEN_ENTER
}

/** The assistant session outside any project: starting or focusing it and answering Claude's trust question for its folder. */
class AssistantState(
    private val scope: CoroutineScope,
    private val terminal: TerminalPort,
    private val live: () -> LiveSessions,
    private val folder: Path,
    private val launch: (show: Boolean) -> Unit,
    private val show: () -> Unit,
    private val enqueue: (ccmSessionId: String, text: String) -> Unit,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val reportError: (title: String, message: String) -> Unit,
    private val pollMillis: Long = POLL_MILLIS,
    private val watchMillis: Long = WATCH_MILLIS,
    private val answerMillis: Long = ANSWER_MILLIS,
) {
    private val pending = mutableListOf<String>()
    private var trustWatcher: Job? = null

    val liveSessionId: String?
        get() = live().of(ClaudeLauncher.ASSISTANT_PROJECT_ID).firstOrNull()?.ccmSessionId

    fun start() {
        val running = liveSessionId
        if (running != null) {
            show()
            terminal.focus(running)
            return
        }
        begin(show = true)
    }

    /** Starts the assistant without leaving the current page; nothing happens when it already runs. */
    fun startInBackground() {
        if (liveSessionId == null) begin(show = false)
    }

    private fun begin(show: Boolean) {
        launch(show)
        trustWatcher?.cancel()
        trustWatcher = scope.launch { watchTrustDialog() }
    }

    /** Queues [text] for the assistant; without a live one it is started and the text waits until its session appears. */
    fun request(text: String) {
        if (text.isBlank()) return
        val running = liveSessionId
        if (running != null) return enqueue(running, text)
        val first = pending.isEmpty()
        pending += text
        if (first) {
            start()
            scope.launch { deliverPending() }
        }
    }

    /** Queued text waits for the claude id: before it the session may still sit on the trust dialog. */
    private suspend fun deliverPending() {
        val delivered = watchSession { session ->
            val ready = session.claudeSessionId != null
            if (ready) {
                pending.forEach { enqueue(session.ccmSessionId, it) }
                pending.clear()
            }
            ready
        }
        if (delivered) return
        val dropped = pending.size
        pending.clear()
        reportError("Assistant did not start", "$dropped queued request(s) were not delivered. Start the Assistant and ask again.")
    }

    /** Claude shows the dialog before SessionStart, so it ends once the session reports its claude id. */
    private suspend fun watchTrustDialog() {
        watchSession { session ->
            session.claudeSessionId != null ||
                (isTrustDialog(terminal.screenText(session.ccmSessionId)).also { if (it) askTrust(session.ccmSessionId) })
        }
    }

    /** Polls until [done] says so; gives up when no session appears within [watchMillis], it exits, or [done] stays false for [answerMillis]. */
    private suspend fun watchSession(done: (SessionSnapshot) -> Boolean): Boolean {
        var waited = 0L
        var seen = false
        while (true) {
            val session = live().of(ClaudeLauncher.ASSISTANT_PROJECT_ID).firstOrNull()
            if (session == null && seen) return false
            if (session != null) {
                if (!seen) waited = 0L
                seen = true
                if (done(session)) return true
            }
            if (waited > (if (seen) answerMillis else watchMillis)) return false
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
        ) { answerYes(ccmSessionId) },
    )

    private fun answerYes(ccmSessionId: String) {
        val screen = terminal.screenText(ccmSessionId)
        if (isTrustDialog(screen)) terminal.send(ccmSessionId, trustYesKeys(screen))
    }
}
