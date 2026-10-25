package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The agents rail's own state: which "Finished" lists are open per session, and the subagent log dialog. The app never
 * types into Claude's terminal to open an agent: [showInTerminal] only moves the user to the session.
 */
class AgentsState(
    private val transcripts: TranscriptService,
    private val scope: CoroutineScope,
    private val live: () -> LiveSessions,
    private val showInTerminal: (String) -> Unit,
) {
    private val finishedOpen = mutableStateMapOf<String, Boolean>()

    var log by mutableStateOf<AgentLog?>(null)
        private set

    fun isFinishedOpen(ccmSessionId: String): Boolean = finishedOpen[ccmSessionId] == true

    fun toggleFinished(ccmSessionId: String) {
        finishedOpen[ccmSessionId] = !isFinishedOpen(ccmSessionId)
    }

    /** Forgets the state of sessions that are gone. */
    fun prune(liveSessionIds: Collection<String>) {
        finishedOpen.keys.retainAll(liveSessionIds.toSet())
    }

    fun openLog(ccmSessionId: String, agent: SubagentSnapshot) {
        log = AgentLog(ccmSessionId, agent, null)
        reload()
    }

    fun reload() {
        val shown = log ?: return
        scope.launch {
            val entries = withContext(Dispatchers.IO) { transcripts.read(current(shown).transcriptPath) }
            if (log?.agent?.agentId == shown.agent.agentId) log = shown.copy(entries = entries)
        }
    }

    /** The agent as the registry shows it now; the opened snapshot once its session is gone. */
    fun current(shown: AgentLog): SubagentSnapshot =
        live().find(shown.ccmSessionId)?.subagents?.firstOrNull { it.agentId == shown.agent.agentId } ?: shown.agent

    fun closeLog() {
        log = null
    }

    fun showSessionInTerminal(ccmSessionId: String) {
        log = null
        showInTerminal(ccmSessionId)
    }
}
