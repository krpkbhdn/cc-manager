package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ClaudeEnvironment
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import kotlinx.coroutines.launch

private val dialogWidth = 520.dp

/** Adds an IDE the app did not find (the project directory is appended to its arguments) and lists the added ones for removal. */
@Composable
fun AddIdeDialog(environment: ClaudeEnvironment) {
    val name = rememberTextFieldState()
    val executable = rememberTextFieldState()
    val arguments = rememberTextFieldState()
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = { environment.ideDialogOpen = false }
    val submit = { environment.addCustomIde(name.text.toString(), executable.text.toString(), arguments.text.toString(), { error = it }, { close() }) }
    ModalHost(close, width = dialogWidth, hasInput = { name.text.isNotEmpty() || executable.text.isNotEmpty() || arguments.text.isNotEmpty() }) {
        DialogBody("Add IDE", Icons.Code, colors.accent) {
            FormField("Name") { AppTextField(name, Modifier.fillMaxWidth(), placeholder = "Zed") }
            FormField("Executable") {
                AppTextField(
                    executable, Modifier.fillMaxWidth(), placeholder = "Path to the program", mono = true,
                    trailing = { IconButton(Icons.FolderOpen, { scope.launch { io { chooseExecutable() }?.let { executable.setTextAndPlaceCursorAtEnd(it.toString()) } } }, tooltip = "Browse") },
                )
            }
            FormField("Arguments", hint = "Optional. The project directory is added after them.", error = error) {
                AppTextField(arguments, Modifier.fillMaxWidth(), placeholder = "--new-window", mono = true, onSubmit = { submit() })
            }
            CustomIdeList(environment)
            DialogActions("Add", name.text.isNotBlank() && executable.text.isNotBlank(), false, { submit() }, close)
        }
    }
}

@Composable
private fun CustomIdeList(environment: ClaudeEnvironment) {
    if (environment.customIdes.isEmpty()) return
    FormField("Added IDEs") {
        environment.customIdes.forEach { ide ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Label(ide.name, Modifier.weight(1f), style = Type.body, tooltipWhenTruncated = true)
                IconButton(Icons.Trash, { environment.deleteCustomIde(ide.id) }, tooltip = "Remove ${ide.name}")
            }
        }
    }
}

private fun chooseExecutable(): Path? {
    val dialog = FileDialog(null as Frame?, "Select the IDE executable", FileDialog.LOAD).apply { isVisible = true }
    return dialog.file?.let { Path.of(dialog.directory, it) }
}
