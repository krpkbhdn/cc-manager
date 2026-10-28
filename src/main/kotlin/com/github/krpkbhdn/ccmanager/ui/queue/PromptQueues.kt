package com.github.krpkbhdn.ccmanager.ui.queue

import androidx.compose.runtime.mutableStateMapOf

class QueuedPrompt(val id: Long, val text: String)

/** Per-session FIFO queues of prompts, in memory only; observable so the panel redraws. Used from the UI thread. */
class PromptQueues {
    private val queues = mutableStateMapOf<String, List<QueuedPrompt>>()
    private var nextId = 1L

    fun of(ccmSessionId: String): List<QueuedPrompt> = queues[ccmSessionId].orEmpty()

    fun add(ccmSessionId: String, text: String) {
        queues[ccmSessionId] = of(ccmSessionId) + QueuedPrompt(nextId++, text)
    }

    fun remove(ccmSessionId: String, promptId: Long) = set(ccmSessionId, of(ccmSessionId).filter { it.id != promptId })

    fun clear(ccmSessionId: String) = set(ccmSessionId, emptyList())

    /** Drops the queues of sessions that are no longer live. */
    fun retainSessions(live: Set<String>) = queues.keys.filter { it !in live }.forEach(queues::remove)

    private fun set(ccmSessionId: String, prompts: List<QueuedPrompt>) {
        if (prompts.isEmpty()) queues.remove(ccmSessionId) else queues[ccmSessionId] = prompts
    }
}
