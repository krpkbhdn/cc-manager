package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanApproval
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanRejection

/** The feedback typed so far and whether approving also auto-accepts edits (off: the user reviews them). */
class PlanDraft {
    val feedback = TextFieldState()
    var autoAcceptEdits by mutableStateOf(false)

    val canRevise: Boolean get() = feedback.text.isNotBlank()

    fun approve() = PlanApproval(autoAcceptEdits)

    /** Null while there is no feedback: a plain Enter in the empty field must do nothing. */
    fun revise() = if (canRevise) PlanRejection(feedback.text.toString().trim()) else null
}
