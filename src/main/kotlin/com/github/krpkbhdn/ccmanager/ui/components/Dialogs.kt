package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.RenameRequest
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.TextField

private val dialogWidth = 420.dp

@Composable
private fun ModalCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(dialogWidth).cardSurface(RoundedCornerShape(Radius.lg))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) { content() }
    }
}

@Composable
private fun DialogActions(confirmLabel: String, confirmEnabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
        SecondaryButton("Cancel", onCancel)
        PrimaryButton(confirmLabel, onConfirm, enabled = confirmEnabled)
    }
}

@Composable
fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit) {
    ModalCard(onDismiss) {
        Label(request.title, size = FontSize.heading, weight = FontWeight.SemiBold)
        Label(request.message, color = colors.textSecondary, maxLines = MESSAGE_LINES)
        DialogActions(request.confirmLabel, true, { onDismiss(); request.onConfirm() }, onDismiss)
    }
}

@Composable
fun RenameDialog(request: RenameRequest, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    val name: TextFieldState = rememberTextFieldState(request.project.name)
    LaunchedEffect(Unit) { name.edit { selectAll() } }
    ModalCard(onDismiss) {
        Label("Rename project", size = FontSize.heading, weight = FontWeight.SemiBold)
        TextField(name, Modifier.fillMaxWidth())
        DialogActions("Rename", name.text.isNotBlank(), { onRename(name.text.toString()) }, onDismiss)
    }
}

private const val MESSAGE_LINES = 4
