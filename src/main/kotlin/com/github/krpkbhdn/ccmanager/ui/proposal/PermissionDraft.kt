package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateListOf
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.Denial
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow

/** What the user ticked under "Remember" and typed as the reason for denying. */
class PermissionDraft(private val proposal: PermissionProposal) {
    val reason = TextFieldState()
    private val remembered = mutableStateListOf<Int>()

    fun isRemembered(suggestion: Int) = suggestion in remembered

    fun toggle(suggestion: Int) {
        if (!remembered.remove(suggestion)) remembered.add(suggestion)
    }

    /** Allows the call and applies the ticked suggestions; none ticked means once. */
    fun allow() = PermissionAllow(remembered.sorted().map { proposal.suggestions()[it].raw() })

    fun deny() = Denial(reason.text.toString().trim())
}
