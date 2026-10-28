package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.RenameRequest
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 420.dp
private val dialogIconBox = 40.dp
private const val MESSAGE_LINES = 5

/**
 * Scrim plus a centred, animated surface; Esc (unless [escapeDismisses] is off) and clicks outside the card call [onDismiss],
 * except while [hasInput] says the form holds typed input: then only the dialog's own Cancel closes it.
 */
@Composable
fun ModalHost(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = dialogWidth,
    takeFocus: Boolean = true,
    escapeDismisses: Boolean = true,
    hasInput: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    val dismissByGesture = { if (!hasInput()) onDismiss() }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; if (takeFocus) focus.requestFocus() }
    val fade by animateFloatAsState(if (shown) 1f else 0f, Motion.base(), label = "scrim")
    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(colors.scrim.copy(alpha = colors.scrim.alpha * fade))
            .onPreviewKeyEvent { event ->
                (escapeDismisses && event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) dismissByGesture() }
            }
            .focusRequester(focus).focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismissByGesture),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.popIn().width(width).heightIn(max = maxHeight - Spacing.lg * 2).card(RoundedCornerShape(Radius.xl), colors.surfaceRaised, raised = true)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) { content() }
    }
}

/** Header and [actions] stay visible; only [content] scrolls when the card is capped by the window height. */
@Composable
fun DialogBody(
    title: String,
    icon: ImageVector,
    tint: Color,
    actions: @Composable (() -> Unit)? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(vertical = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(dialogIconBox).clip(RoundedCornerShape(Radius.md)).background(tint.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(icon, tint, size = IconSize.xlarge)
            }
            Label(title, style = Type.subtitle)
            headerTrailing()
        }
        val scroll = rememberScrollState()
        // The scrollbar fills the box height, so it sits in a matchParentSize box that cannot stretch the card.
        Box(Modifier.weight(1f, fill = false)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) { content() }
            Box(Modifier.matchParentSize()) { ThinScrollbar(scroll) }
        }
        actions?.let { Box(Modifier.padding(horizontal = Spacing.xl)) { it() } }
    }
}

@Composable
fun DialogActions(confirmLabel: String, confirmEnabled: Boolean, destructive: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
        AppButton("Cancel", onCancel)
        AppButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.DANGER else ButtonVariant.PRIMARY, enabled = confirmEnabled)
    }
}

@Composable
fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit) {
    ModalHost(onDismiss) {
        DialogBody(request.title, Icons.Alert, if (request.destructive) colors.danger else colors.accent) {
            Label(request.message, color = colors.textSecondary, maxLines = MESSAGE_LINES)
            DialogActions(request.confirmLabel, true, request.destructive, { onDismiss(); request.onConfirm() }, onDismiss)
        }
    }
}

@Composable
fun RenameDialog(request: RenameRequest, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    val name: TextFieldState = rememberTextFieldState(request.project.name)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus(); name.edit { selectAll() } }
    val submit = { if (name.text.isNotBlank()) onRename(name.text.toString()) }
    ModalHost(onDismiss, takeFocus = false) {
        DialogBody("Rename project", Icons.Edit, colors.accent) {
            AppTextField(name, Modifier.fillMaxWidth(), placeholder = "Project name", focusRequester = focus, onSubmit = submit)
            DialogActions("Rename", name.text.isNotBlank(), false, submit, onDismiss)
        }
    }
}
