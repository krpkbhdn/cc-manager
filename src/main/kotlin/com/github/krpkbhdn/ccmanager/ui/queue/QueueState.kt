package com.github.krpkbhdn.ccmanager.ui.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Prompts waiting for the live sessions to be free; the sending decision lives in [QueueDispatch]. Runs on the UI scope only. */
class QueueState(
    private val registry: SessionRegistry,
    terminal: TerminalPort,
    private val scope: CoroutineScope,
    isAttached: (String) -> Boolean,
    sendPrompt: suspend (ccmSessionId: String, text: String) -> Boolean,
) {
    private val queues = PromptQueues()
    private val dispatch = QueueDispatch(queues, registry::sessions, terminal::draftMayExist, isAttached, sendPrompt, scope)
    private var unsubscribe: Runnable? = null

    /** The session whose queue panel is open, if any. */
    var panelSessionId by mutableStateOf<String?>(null)

    fun of(ccmSessionId: String): List<QueuedPrompt> = queues.of(ccmSessionId)

    fun add(ccmSessionId: String, text: String) {
        if (text.isBlank()) return
        queues.add(ccmSessionId, text.trim())
        dispatch.promptAdded(ccmSessionId)
        dispatch.evaluate()
    }

    fun remove(ccmSessionId: String, promptId: Long) = queues.remove(ccmSessionId, promptId)

    fun clear(ccmSessionId: String) = queues.clear(ccmSessionId)

    fun start() {
        unsubscribe = registry.subscribe { scope.launch { dispatch.evaluate() } }
    }

    fun dispose() {
        unsubscribe?.run()
        dispatch.dispose()
    }
}
