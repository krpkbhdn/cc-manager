package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantState
import com.github.krpkbhdn.ccmanager.ui.proposal.ProposalState
import com.github.krpkbhdn.ccmanager.ui.sessions.nextWaiting

/** Which pending proposal the open screen shows, and the jumps from other screens to a session that waits for an answer. */
class ProposalAnswers(
    private val proposals: ProposalState,
    private val assistant: AssistantState,
    private val route: () -> Route,
    private val viewOf: (Long) -> ProjectView,
    private val live: () -> LiveSessions,
    private val toast: (NotificationKind, String, String) -> Unit,
    private val openSession: (String) -> Unit,
) {
    /** The proposal dialog to show: the oldest unhidden proposal of the session whose terminal tab is open. */
    val dialog: Proposal?
        get() {
            val current = route()
            if (current == Route.Assistant) return assistant.liveSessionId?.let { proposals.dialogFor(it) }
            val project = current as? Route.Project ?: return null
            val session = viewOf(project.id).selected as? ProjectTab.Session ?: return null
            return proposals.dialogFor(session.ccmSessionId)
        }

    /** Answers the next session waiting for the user after the focused one; false when none waits. */
    fun answerNextWaiting(): Boolean {
        val focused = (route() as? Route.Project)?.let { viewOf(it.id).focusedSession }
        val next = nextWaiting(live().sessions, focused)
        if (next == null) {
            toast(NotificationKind.INFO, "No session is waiting", "Nothing needs your answer right now.")
            return false
        }
        answer(next.ccmSessionId)
        return true
    }

    /** Opens the session and shows its proposals again, also those the user hid before. */
    fun answer(ccmSessionId: String) {
        proposals.reopen(ccmSessionId)
        openSession(ccmSessionId)
    }
}
