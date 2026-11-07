package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.state.commandBlockedReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val QUEUE_RETRY_MILLIS = 1_000L
const val QUEUE_MAX_RETRIES = 600

/** Sends queued prompts through [sendPrompt] as [QueueDispatcher] allows and re-checks on a timer while one waits, up to [maxRetries] times until the next [evaluate] (a status change) restarts it. Runs on [scope] only. */
class QueueDispatch(
    private val queues: PromptQueues,
    private val sessions: () -> List<SessionSnapshot>,
    private val draftMayExist: (String) -> Boolean,
    private val isAttached: (String) -> Boolean,
    private val sendPrompt: suspend (ccmSessionId: String, text: String) -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMillis: Long = QUEUE_RETRY_MILLIS,
    private val maxRetries: Int = QUEUE_MAX_RETRIES,
) {
    private val dispatcher = QueueDispatcher()
    private val sending = mutableMapOf<String, Job>()
    private var retry: Job? = null
    private var disposed = false
    private var retries = 0

    fun promptAdded(ccmSessionId: String) = dispatcher.promptAdded(ccmSessionId)

    fun evaluate() {
        retries = 0
        check()
    }

    private fun check() {
        val snapshots = sessions()
        val live = snapshots.map { it.ccmSessionId }.toSet()
        queues.retainSessions(live)
        dispatcher.retainSessions(live)
        var waiting = false
        snapshots.forEach { session ->
            if (dispatcher.observe(session.ccmSessionId, observationOf(session)) == QueueDecision.SEND) dispatch(session.ccmSessionId)
            waiting = waiting || dispatcher.needsRecheck(session.ccmSessionId)
        }
        if (waiting) scheduleRetry()
    }

    fun dispose() {
        disposed = true
        retry?.cancel()
        sending.values.forEach { it.cancel() }
        sending.clear()
    }

    private fun observationOf(session: SessionSnapshot): QueueObservation {
        val id = session.ccmSessionId
        return QueueObservation(
            status = session.status,
            interrupted = session.activity == ActivityFormatter.INTERRUPTED,
            queueEmpty = queues.of(id).isEmpty(),
            draftExists = draftMayExist(id),
            blockedReason = commandBlockedReason(session, isAttached(id)),
            nowMillis = clock(),
        )
    }

    private fun dispatch(ccmSessionId: String) {
        val prompt = queues.of(ccmSessionId).firstOrNull() ?: return
        if (disposed || sending.containsKey(ccmSessionId)) return
        sending[ccmSessionId] = scope.launch {
            try {
                if (withContext(NonCancellable) { sendPrompt(ccmSessionId, prompt.text) }) {
                    queues.remove(ccmSessionId, prompt.id)
                    dispatcher.sent(ccmSessionId, clock())
                }
            } finally {
                sending.remove(ccmSessionId)
            }
            if (dispatcher.needsRecheck(ccmSessionId)) scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (disposed || retry?.isActive == true || retries >= maxRetries) return
        retries++
        retry = scope.launch {
            delay(retryMillis)
            retry = null
            check()
        }
    }
}
