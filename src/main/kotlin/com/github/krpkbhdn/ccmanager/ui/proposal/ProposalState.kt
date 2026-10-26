package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pending questions, plans, permissions and forms per session; answers go to the backend off the UI thread.
 * Closing a dialog only hides its proposal (it stays pending and can be reopened); what the user typed survives in [draft].
 */
class ProposalState(
    private val service: ProposalService,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
    private val focusTerminal: (String) -> Unit = {},
) {
    private var bySession by mutableStateOf<Map<String, List<Proposal>>>(emptyMap())
    private var hidden by mutableStateOf<Set<String>>(emptySet())
    private val drafts = mutableMapOf<String, Any>()
    private var unsubscribe: Runnable? = null
    private val reload = Refresher(scope) {
        runGuarded(reportError) {
            bySession = io { service.pending() }.groupBy { it.ccmSessionId() }
            val alive = bySession.values.flatten().map { it.id() }.toSet()
            hidden = hidden intersect alive
            drafts.keys.retainAll(alive)
        }
    }

    fun start() {
        unsubscribe = service.subscribe { reload.request() }
        reload.request()
    }

    fun dispose() {
        unsubscribe?.run()
    }

    /** Oldest first. */
    fun of(ccmSessionId: String): List<Proposal> = bySession[ccmSessionId].orEmpty()

    fun headlineOf(ccmSessionId: String): String? = of(ccmSessionId).firstOrNull()?.let(::proposalHeadline)

    /** The proposal whose dialog is open for the session: the oldest one the user has not hidden. */
    fun dialogFor(ccmSessionId: String): Proposal? = of(ccmSessionId).firstOrNull { it.id() !in hidden }

    fun hiddenCount(ccmSessionId: String): Int = of(ccmSessionId).count { it.id() in hidden }

    /** "1 of 3", or null when it is the only one. */
    fun positionOf(proposal: Proposal): String? =
        of(proposal.ccmSessionId()).takeIf { it.size > 1 }?.let { "${it.indexOfFirst { p -> p.id() == proposal.id() } + 1} of ${it.size}" }

    fun hide(proposal: Proposal) {
        hidden = hidden + proposal.id()
    }

    /** A proposal dialog went away, whatever the reason: the keyboard goes back to the terminal unless another dialog takes it. */
    fun dialogClosed(ccmSessionId: String) = focusTerminal(ccmSessionId)

    fun reopen(ccmSessionId: String) {
        hidden = hidden - of(ccmSessionId).map { it.id() }.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> draft(proposal: Proposal, create: () -> T): T = drafts.getOrPut(proposal.id(), create) as T

    /** The dialog goes away only once the backend took the answer, so a failure can be retried in place. */
    fun answer(proposal: Proposal, answer: ProposalAnswer) {
        scope.launch {
            runGuarded(reportError) {
                io { service.answer(proposal.id(), answer) }
                hide(proposal)
            }
        }
    }

    /** Drops the proposal from the app; the terminal dialog stays in charge. */
    fun dismiss(proposal: Proposal) {
        scope.launch {
            runGuarded(reportError) {
                io { service.dismiss(proposal.id()) }
                hide(proposal)
            }
        }
    }
}
