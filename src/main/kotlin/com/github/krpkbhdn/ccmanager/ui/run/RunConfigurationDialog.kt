package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.picker.FolderPickerDialog
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.nio.file.Path

private val dialogWidth = 560.dp

/** Create or edit one run configuration; the backend's validation messages are shown under the fields. */
@Composable
fun RunConfigurationDialog(form: RunDialogState, runs: RunState, dataDir: Path) {
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { runs.save(form) { error = it } }
    ModalHost(runs::closeDialog, width = dialogWidth, takeFocus = false, hasInput = { form.hasInput }) {
        DialogBody(if (form.existing == null) "New run configuration" else "Edit run configuration", Icons.Play, colors.accent) {
            Field("Name") { AppTextField(form.name, Modifier.fillMaxWidth(), placeholder = "dev server", focusRequester = focus, onSubmit = save) }
            Field("Command") { AppTextField(form.command, Modifier.fillMaxWidth(), mono = true, placeholder = "npm run dev", onSubmit = save) }
            Field("Directory") {
                AppTextField(
                    form.directory, Modifier.fillMaxWidth(), mono = true, onSubmit = save,
                    trailing = { IconButton(Icons.FolderOpen, { picking = true }, tooltip = "Choose folder") },
                )
            }
            Footer(error, form.existing?.let { { runs.requestDelete(it) } }, save, runs::closeDialog)
        }
    }
    if (picking) {
        val current = runCatching { Path.of(form.directory.text.toString().trim()) }.getOrNull()
        FolderPickerDialog(current, dataDir, { picking = false }) { form.directory.setTextAndPlaceCursorAtEnd(it.toString()); picking = false }
    }
}

@Composable
private fun Footer(error: String?, onDelete: (() -> Unit)?, onSave: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        onDelete?.let { AppButton("Delete", it, variant = ButtonVariant.GHOST, icon = Icons.Trash) }
        Label(error.orEmpty(), Modifier.weight(1f), color = colors.danger, style = Type.small, maxLines = 2, tooltipWhenTruncated = true)
        AppButton("Cancel", onCancel)
        AppButton("Save", onSave, variant = ButtonVariant.PRIMARY)
    }
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(title, color = colors.textSecondary, style = Type.smallMedium)
        content()
    }
}
