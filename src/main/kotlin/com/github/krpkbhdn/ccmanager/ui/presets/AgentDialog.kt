package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private const val CONTENT_LINES = 14
private val starter = """
    ---
    name: reviewer
    description: Reviews a diff for missing tests and unclear names. Use after a change is made.
    tools: Read, Grep, Glob
    ---

    You review code changes. Report problems by severity and point at the line.
""".trimIndent() + "\n"

/** A subagent definition (markdown with front matter) copied to the project's .claude/agents folder. */
@Composable
fun AgentDialog(open: ComponentDialog, state: PresetsState) {
    val editing = open.editing
    val initialName = editing?.name().orEmpty()
    val initialContent = editing?.payload()?.text("content") ?: starter
    val name = rememberTextFieldState(initialName)
    val content = rememberTextFieldState(initialContent)
    val nameError = name.text.toString().takeIf { it.isNotBlank() }?.let { folderNameProblem(it) }
    ComponentDialogFrame(
        if (editing == null) "Add agent" else "Edit agent", Icons.Bot, if (editing == null) "Add agent" else "Save",
        name.text.isNotBlank() && nameError == null && content.text.isNotBlank(),
        { onError -> state.save(open, PresetComponentKind.AGENT, name.text.toString(), PresetPayloads.agent(content.text.toString()), onError) },
        state::closeDialog,
        hasInput = { name.text.toString() != initialName || content.text.toString() != initialContent },
    ) {
        FormField("Name", error = nameError, hint = "Also the file name: .claude/agents/<name>.md") {
            AppTextField(name, Modifier.fillMaxWidth(), placeholder = "reviewer", mono = true, height = inputHeight)
        }
        FormField("Definition", hint = "Markdown with front matter (name, description, tools, model); the body is the agent's system prompt") {
            AppTextArea(content, Modifier.fillMaxWidth(), mono = true, minLines = CONTENT_LINES, maxLines = CONTENT_LINES)
        }
    }
}
