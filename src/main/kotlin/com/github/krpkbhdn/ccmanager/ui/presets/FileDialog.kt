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

private const val CONTENT_LINES = 7
private const val IGNORE_LINES = 2

/** A file created in the project, plus optional lines the project's .gitignore gets. */
@Composable
fun FileDialog(open: ComponentDialog, state: PresetsState) {
    val editing = open.editing
    val payload = editing?.payload()
    val initialPath = payload?.text("path").orEmpty()
    val initialContent = payload?.text("content").orEmpty()
    val initialIgnore = payload?.gitignoreLines()?.joinToString("\n").orEmpty()
    val path = rememberTextFieldState(initialPath)
    val content = rememberTextFieldState(initialContent)
    val ignore = rememberTextFieldState(initialIgnore)
    val pathError = path.text.toString().takeIf { it.isNotBlank() }?.let { pathProblem(it) }
    ComponentDialogFrame(
        if (editing == null) "Add file" else "Edit file", Icons.FileEdit, if (editing == null) "Add file" else "Save",
        path.text.isNotBlank() && pathError == null,
        { onError ->
            val cleanPath = path.text.toString().trim().replace('\\', '/')
            state.save(open, PresetComponentKind.FILE, cleanPath, PresetPayloads.file(cleanPath, content.text.toString(), parseGitignore(ignore.text.toString())), onError)
        },
        state::closeDialog,
        hasInput = { path.text.toString() != initialPath || content.text.toString() != initialContent || ignore.text.toString() != initialIgnore },
    ) {
        FormField("Path in the project", error = pathError, hint = "Relative, for example .jscpd.json or docs/rules.md. A different file already there is left unchanged.") {
            AppTextField(path, Modifier.fillMaxWidth(), placeholder = ".jscpd.json", mono = true, icon = Icons.FileEdit, height = inputHeight)
        }
        FormField("Content") {
            AppTextArea(content, Modifier.fillMaxWidth(), mono = true, minLines = CONTENT_LINES, maxLines = CONTENT_LINES)
        }
        FormField("Add to .gitignore", hint = "One pattern per line; leave empty to touch nothing") {
            AppTextArea(ignore, Modifier.fillMaxWidth(), placeholder = "jscpd-report/", mono = true, minLines = IGNORE_LINES, maxLines = IGNORE_LINES)
        }
    }
}
