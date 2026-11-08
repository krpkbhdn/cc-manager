package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot

/** Review comments of one project and the bookkeeping of the prompt that sent them, so the diff can be rechecked when the turn ends. */
class ReviewComments {
    private val items = mutableStateListOf<ReviewComment>()
    var sentTo by mutableStateOf<String?>(null)
        private set
    var promptId by mutableStateOf<Long?>(null)
        private set
    var recheckOffered by mutableStateOf(false)
        private set

    val all: List<ReviewComment> get() = items

    fun add(comment: ReviewComment) { items.add(comment) }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        clearSentIfNone()
    }

    fun open(): List<ReviewComment> = items.filter { it.status == ReviewStatus.OPEN }

    fun forPath(path: String): List<ReviewComment> = items.filter { it.path == path }

    fun markSent(ids: Set<String>, ccmSessionId: String, promptId: Long?) {
        update { if (it.id in ids) it.copy(status = ReviewStatus.SENT) else it }
        sentTo = ccmSessionId
        this.promptId = promptId
        recheckOffered = false
    }

    fun relocated(id: String, range: IntRange?) {
        update { if (it.id == id) it.moved(range) else it }
    }

    fun recheck(results: Map<String, IntRange?>) {
        update { if (it.id in results) it.moved(results[it.id]) else it }
        clearSentIfNone()
    }

    /** Hands SENT comments back as OPEN so they can be sent again, e.g. when the prompt never reached the agent. */
    fun reopenSent() {
        update { if (it.status == ReviewStatus.SENT) it.copy(status = ReviewStatus.OPEN) else it }
        clearSentIfNone()
    }

    fun statusOf(id: String): ReviewStatus? = items.firstOrNull { it.id == id }?.status

    fun clearRecheckOffer() { recheckOffered = false }

    /** True once, when the session the comments went to finished a turn on its own and the prompt is no longer queued. */
    fun turnEnded(previous: Map<String, AgentStatus>, now: List<SessionSnapshot>, queued: (String) -> List<Long>): Boolean {
        val target = sentTo ?: return false
        if (clearSentIfNone()) return false
        val session = now.firstOrNull { it.ccmSessionId() == target } ?: return false
        val finished = previous[target] == AgentStatus.WORKING && session.status() == AgentStatus.IDLE
        val pending = promptId?.let { it in queued(target) } ?: false
        if (!finished || session.activity() == ActivityFormatter.INTERRUPTED || pending) return false
        forgetPrompt()
        recheckOffered = true
        return true
    }

    private fun clearSentIfNone(): Boolean {
        if (items.any { it.status == ReviewStatus.SENT }) return false
        forgetPrompt()
        return true
    }

    private fun forgetPrompt() {
        sentTo = null
        promptId = null
    }

    private fun ReviewComment.moved(range: IntRange?) =
        if (range == null) copy(status = ReviewStatus.STALE) else copy(lines = range, status = ReviewStatus.OPEN)

    private fun update(transform: (ReviewComment) -> ReviewComment) {
        items.indices.forEach { items[it] = transform(items[it]) }
    }
}
