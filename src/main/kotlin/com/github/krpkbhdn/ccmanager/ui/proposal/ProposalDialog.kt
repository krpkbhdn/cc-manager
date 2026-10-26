package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.proposal.FormProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val SUBAGENT_LABEL = "subagent"

/** The dialog of the proposal the open session waits on, if any; sessions that are not on screen never pop up. */
@Composable
fun ProposalDialogHost(state: AppState) {
    val proposal = state.proposalDialog ?: return
    val context = remember(proposal.id()) { ProposalContext(state.proposals, proposal) }
    val title = titleOf(proposal, agentLabel(state, proposal), state.proposals.positionOf(proposal))
    key(proposal.id()) {
        DisposableEffect(Unit) { onDispose { state.proposals.dialogClosed(proposal.ccmSessionId()) } }
        when (proposal) {
            is QuestionsProposal -> QuestionsDialog(proposal, title, context)
            is PlanProposal -> PlanDialog(proposal, title, context)
            is PermissionProposal -> PermissionDialog(proposal, title, context)
            is FormProposal -> FormDialog(proposal, title, context)
        }
    }
}

private fun agentLabel(state: AppState, proposal: Proposal): String? =
    proposal.agentId().takeIf { it.isNotEmpty() }?.let { id ->
        state.live.find(proposal.ccmSessionId())?.subagents?.firstOrNull { it.agentId() == id }?.agentType() ?: SUBAGENT_LABEL
    }

@Composable
private fun titleOf(proposal: Proposal, agent: String?, position: String?): FrameTitle = when (proposal) {
    is QuestionsProposal -> FrameTitle(Icons.Message, "Claude asks", colors.accent, listOfNotNull(agent), position)
    is PlanProposal -> FrameTitle(Icons.Tasks, "Plan approval", colors.accent, listOfNotNull(agent), position)
    is PermissionProposal -> FrameTitle(Icons.Shield, "Permission", colors.warning, listOfNotNull(proposal.toolName(), agent), position)
    is FormProposal -> FrameTitle(Icons.Plug, "Input requested", colors.accent, listOfNotNull(agent), position)
}

internal val QUESTIONS_WIDTH = 640.dp
internal val QUESTIONS_PREVIEW_WIDTH = 820.dp
internal val PLAN_WIDTH = 760.dp
internal val PERMISSION_WIDTH = 620.dp
internal val FORM_WIDTH = 560.dp
