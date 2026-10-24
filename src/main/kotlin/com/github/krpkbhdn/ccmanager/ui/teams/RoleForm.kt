package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.team.RoleDraft

/** Editable fields of one role; [draft] throws the backend's validation message while the fields are not valid yet. */
class RoleForm(source: RoleDraft?, lead: Boolean = source?.lead ?: false) {
    val name = TextFieldState(source?.name.orEmpty())
    val description = TextFieldState(source?.description.orEmpty())
    val prompt = TextFieldState(source?.prompt.orEmpty())
    var model by mutableStateOf(source?.model)
    var effort by mutableStateOf(source?.effort)
    var tools by mutableStateOf(ToolSelection.parse(source?.tools))
    var lead by mutableStateOf(lead)

    private val initial = signature()

    fun draft(): RoleDraft {
        tools.error?.let { throw IllegalArgumentException(it) }
        return RoleDraft(name.text.toString(), description.text.toString(), prompt.text.toString(), model, effort, tools.serialize(), lead)
    }

    val error: String? get() = runCatching { draft() }.exceptionOrNull()?.message

    val dirty: Boolean get() = signature() != initial

    private fun signature() = listOf(name.text, description.text, prompt.text, model, effort, tools.tools, lead).map { it.toString() }
}
