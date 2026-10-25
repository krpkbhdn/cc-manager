package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal.Suggestion
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.Denial
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanApproval
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanRejection
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class DialogDraftsTest {
    private val mapper = JsonMapper.builder().build()
    private val first = mapper.readTree("""{"type":"addRules"}""")
    private val second = mapper.readTree("""{"type":"setMode"}""")
    private val proposal = PermissionProposal("p", "s", Instant.EPOCH, "", "Bash", mapper.createObjectNode(), listOf(Suggestion(first, "rule"), Suggestion(second, "mode")))

    @Test
    fun allowWithNothingTickedIsAllowOnce() {
        assertThat(PermissionDraft(proposal).allow()).isEqualTo(PermissionAllow.once())
    }

    @Test
    fun allowAppliesTheTickedSuggestionsInOrder() {
        val draft = PermissionDraft(proposal)
        draft.toggle(1)
        draft.toggle(0)

        assertThat(draft.allow()).isEqualTo(PermissionAllow(listOf(first, second)))
        draft.toggle(0)
        assertThat(draft.allow()).isEqualTo(PermissionAllow(listOf(second)))
    }

    @Test
    fun denyCarriesTheTrimmedReason() {
        val draft = PermissionDraft(proposal)
        draft.reason.edit { replace(0, length, " use npm ") }

        assertThat(draft.deny()).isEqualTo(Denial("use npm"))
    }

    @Test
    fun planApprovalFollowsTheEditsChoiceAndReviseNeedsFeedback() {
        val draft = PlanDraft()

        assertThat(draft.approve()).isEqualTo(PlanApproval(false))
        assertThat(draft.revise()).isNull()
        draft.autoAcceptEdits = true
        draft.feedback.edit { replace(0, length, " smaller ") }

        assertThat(draft.approve()).isEqualTo(PlanApproval(true))
        assertThat(draft.revise()).isEqualTo(PlanRejection("smaller"))
    }
}
