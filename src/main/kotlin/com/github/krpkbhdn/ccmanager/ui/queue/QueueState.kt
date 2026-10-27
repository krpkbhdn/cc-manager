package com.github.krpkbhdn.ccmanager.ui.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityFormatter
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import com.github.krpkbhdn.ccmanager.ui.state.commandBlockedReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RETRY_MILLIS = 1_000L

/** Prompts waiting for the live sessions to finish their turn; sends one per turn end through [sendPrompt]. Runs on the UI scope only. */
class QueueState(
    private val registry: SessionRegistry,
    private val terminal: TerminalPort,
    private val scope: CoroutineScope,
    private val isAttached: (String) -> Boolean,
    private val sendPrompt: suspend (ccmSessionId: String, text: String) -> Boolean,
) {
    private val queues = PromptQueues()
    private val dispatcher = QueueDispatcher()
    private val sending = mutableMapOf<String, Job>()
    private var retry: Job? = null
    private var disposed = false
    private var unsubscribe: Runnable? = null

    /** The session whose queue panel is open, if any. */
    var panelSessionId by mutableStateOf<String?>(null)

    fun of(ccmSessionId: String): List<QueuedPrompt> = queues.of(ccmSessionId)

    fun add(ccmSessionId: String, text: String) {
        if (text.isNotBlank()) queues.add(ccmSessionId, text.trim())
    }

    fun remove(ccmSessionId: String, promptId: Long) = queues.remove(ccmSessionId, promptId)

    fun clear(ccmSessionId: String) = queues.clear(ccmSessionId)

    fun start() {
        unsubscribe = registry.subscribe { scope.launch { evaluate() } }
    }

    fun dispose() {
        disposed = true
        unsubscribe?.run()
        retry?.cancel()
        sending.values.forEach { it.cancel() }
        sending.clear()
    }

    private fun evaluate() {
        val sessions = registry.sessions()
        val live = sessions.map { it.ccmSessionId }.toSet()
        queues.retainSessions(live)
        dispatcher.retainSessions(live)
        var waiting = false
        sessions.forEach { session ->
            if (dispatcher.observe(session.ccmSessionId, observationOf(session)) == QueueDecision.SEND) dispatch(session.ccmSessionId)
            waiting = waiting || dispatcher.isWaiting(session.ccmSessionId)
        }
        if (waiting) scheduleRetry()
    }

    private fun observationOf(session: SessionSnapshot): QueueObservation {
        val id = session.ccmSessionId
        return QueueObservation(
            status = session.status,
            interrupted = session.activity == ActivityFormatter.INTERRUPTED,
            queueEmpty = queues.of(id).isEmpty(),
            draftExists = terminal.draftMayExist(id),
            blockedReason = commandBlockedReason(session, isAttached(id)),
        )
    }

    private fun dispatch(ccmSessionId: String) {
        val prompt = queues.of(ccmSessionId).firstOrNull() ?: return
        if (disposed || sending.containsKey(ccmSessionId)) return
        sending[ccmSessionId] = scope.launch {
            try {
                if (sendPrompt(ccmSessionId, prompt.text)) {
                    queues.remove(ccmSessionId, prompt.id)
                    dispatcher.sent(ccmSessionId)
                }
            } finally {
                sending.remove(ccmSessionId)
            }
            if (dispatcher.isWaiting(ccmSessionId)) scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (disposed || retry?.isActive == true) return
        retry = scope.launch {
            delay(RETRY_MILLIS)
            retry = null
            evaluate()
        }
    }
}
