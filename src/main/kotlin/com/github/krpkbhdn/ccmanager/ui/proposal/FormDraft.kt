package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.FormAnswer

/** The values typed into an MCP form so far; errors are only shown after the first failed attempt to accept. */
class FormDraft(val fields: List<FormField>) {
    val values = mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, it.initial) } }
    var showErrors by mutableStateOf(false)
        private set

    val errors: Map<String, String> get() = formErrors(fields, values)

    /** The accepting answer, or null (and the errors become visible) while a field still has to be fixed. */
    fun accept(): FormAnswer? {
        if (errors.isNotEmpty()) {
            showErrors = true
            return null
        }
        return FormAnswer.accept(formContent(fields, values))
    }
}
