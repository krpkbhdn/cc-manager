package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.GroupDialogRequest
import com.github.krpkbhdn.ccmanager.ui.state.ProjectsState
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Creates a group or edits one: its name and which ungrouped projects (plus its current members) belong to it. */
@Composable
fun GroupDialog(request: GroupDialogRequest, projects: ProjectsState) {
    val group = request.group
    val currentMembers = remember { group?.let { projects.membersOf(it.id) }.orEmpty() }
    val name = rememberTextFieldState(group?.name.orEmpty())
    val chosen = remember { currentMembers.map { it.id }.toMutableStateList() }
    val candidates = remember { projects.ungrouped + currentMembers }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(name.text.toString(), chosen.size) { error = null }
    val valid = name.text.isNotBlank() && chosen.isNotEmpty()
    val close = { projects.groupDialog = null }
    val save = { if (valid) projects.saveGroup(group, name.text.toString(), chosen.toList()) { error = it } }
    ModalHost(close, takeFocus = false) {
        DialogBody(if (group == null) "New group" else "Edit group", Icons.Layers, colors.accent) {
            AppTextField(name, Modifier.fillMaxWidth(), placeholder = "Group name", focusRequester = focus, onSubmit = save)
            Label("Projects in the group", color = colors.textSecondary, style = Type.smallMedium)
            if (candidates.isEmpty()) Label("Every project already belongs to a group.", color = colors.textTertiary, style = Type.caption)
            candidates.forEach { project ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    AppCheckbox(project.id in chosen, { on -> if (on) chosen += project.id else chosen -= project.id })
                    Label(project.name, style = Type.bodyMedium)
                }
            }
            error?.let { Label(it, color = colors.danger, style = Type.small, maxLines = 3) }
            DialogActions(if (group == null) "Create" else "Save", valid, false, save, close)
        }
    }
}
