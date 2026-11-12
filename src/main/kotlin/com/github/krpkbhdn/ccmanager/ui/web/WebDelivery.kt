package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.review.SessionChoice
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.web.WebReviewService
import com.github.krpkbhdn.ccmanager.web.WebRevision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class WebSendTarget(val revision: WebRevision, val candidates: List<SessionChoice>)

typealias SessionEnqueue = (ccmSessionId: String, text: String) -> Unit

typealias SessionStarter = (projectId: Long, prompt: String, onNotStarted: () -> Unit, onStarted: (String) -> Unit) -> Unit

private const val START_FAILED = "Could not start a session"

/** Sends a revision to an agent: the session chooser per project, binding the session to the revision, and one send at a time per revision. */
internal class WebDelivery(
    private val service: WebReviewService,
    private val enqueue: SessionEnqueue,
    private val startSession: SessionStarter,
    private val live: () -> LiveSessions,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    private val targets = mutableStateMapOf<Long, WebSendTarget>()
    private var sending by mutableStateOf(emptySet<Long>())

    fun sendTarget(projectId: Long): WebSendTarget? = targets[projectId]

    fun isSending(revisionId: Long): Boolean = revisionId in sending

    fun send(revision: WebRevision) = launchOnce(revision) {
        val bound = revision.ccmSessionId()
        if (bound == null) offerTargets(revision) else deliver(revision, bound)
    }

    fun sendTo(revision: WebRevision, ccmSessionId: String?) = launchOnce(revision) {
        targets.remove(revision.projectId())
        deliver(revision, ccmSessionId)
    }

    fun dismissSendTarget(projectId: Long) {
        targets.remove(projectId)
    }

    private fun launchOnce(revision: WebRevision, work: suspend () -> Unit) {
        val revisionId = revision.id()
        if (!begin(revisionId)) return
        scope.launch {
            try {
                runGuarded(reportError) { work() }
            } finally {
                end(revisionId)
            }
        }
    }

    @Synchronized
    private fun begin(revisionId: Long): Boolean {
        if (revisionId in sending) return false
        sending = sending + revisionId
        return true
    }

    @Synchronized
    private fun end(revisionId: Long) {
        sending = sending - revisionId
    }

    private fun offerTargets(revision: WebRevision) {
        val candidates = live().of(revision.projectId()).map { SessionChoice(it.ccmSessionId, it.title?.takeIf(String::isNotBlank) ?: it.ccmSessionId) }
        targets[revision.projectId()] = WebSendTarget(revision, candidates)
    }

    /** A dead target reopens the chooser before anything is marked sent; the chosen or started session is bound to the revision. */
    private suspend fun deliver(revision: WebRevision, ccmSessionId: String?) {
        if (ccmSessionId != null && live().find(ccmSessionId) == null) {
            offerTargets(revision)
            return
        }
        val feedback = io { service.prepareFeedback(revision.id()) }
        if (ccmSessionId != null) {
            io { service.bindSession(revision.id(), ccmSessionId) }
            enqueue(ccmSessionId, feedback.prompt())
        } else {
            startSession(revision.projectId(), feedback.prompt(), { reportError("$START_FAILED. Feedback is in ${feedback.markdown()}") }, { started ->
                scope.launch { runGuarded(reportError) { io { service.bindSession(revision.id(), started) } } }
            })
        }
    }
}
