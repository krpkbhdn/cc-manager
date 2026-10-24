package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.nio.file.Path

/**
 * Test-source stand-in for the real picker (the production class is still a stub in this branch): shadows the
 * production class on the preview classpath and takes a typed path.
 */
@Composable
fun FolderPickerDialog(initial: Path?, onDismiss: () -> Unit, onPicked: (Path) -> Unit) {
    val path = rememberTextFieldState(initial?.toString() ?: "")
    ModalHost(onDismiss, takeFocus = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label("Choose a project folder (preview placeholder)")
            AppTextField(path, Modifier.fillMaxWidth(), placeholder = "C:\\path\\to\\folder", mono = true, onSubmit = { onPicked(Path.of(path.text.toString())) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                AppButton("Cancel", onDismiss)
                AppButton("Add", { onPicked(Path.of(path.text.toString())) }, variant = ButtonVariant.PRIMARY, enabled = path.text.isNotBlank())
            }
            Label("Replace with the real picker once it is merged.", color = colors.textTertiary)
        }
    }
}
