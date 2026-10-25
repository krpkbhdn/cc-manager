package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.extensions.description
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 480.dp

/** Creates a skill folder with a starter SKILL.md in the context's own scope and selects it. */
@Composable
fun NewSkillDialog(context: ExtensionContext, state: SkillsState) {
    val scope = newSkillScope(context)
    val name = rememberTextFieldState()
    val description = rememberTextFieldState()
    val nameText = name.text.toString().trim()
    val nameError = if (nameText.isEmpty()) null else skillNameProblem(nameText, state.skills.filter { it.scope == scope && !it.readOnly() }.map { it.name })
    val canCreate = nameText.isNotEmpty() && nameError == null && skillDescriptionProblem(description.text.toString()) == null && state.job == null
    val create = { if (canCreate) state.create(scope, nameText, description.text.toString()) }
    val dismiss = { state.creating = false }
    ModalHost(dismiss, width = dialogWidth) {
        DialogBody("New skill", Icons.Book, colors.accent, actions = { DialogActions("Create skill", canCreate, false, create, dismiss) }) {
            FormField("Name", error = nameError, hint = "Also the folder name and the /command, for example code-review") {
                AppTextField(name, Modifier.fillMaxWidth(), placeholder = "skill-name", mono = true)
            }
            FormField("Description", hint = "Claude reads this to decide when to use the skill") {
                AppTextArea(description, Modifier.fillMaxWidth(), placeholder = "Reviews a diff for missing tests. Use when the user asks for a review.", minLines = 3, maxLines = 5, onSubmit = create)
            }
            FormField("Created in", hint = scope.description()) { ScopeBadge(scope) }
        }
    }
}
