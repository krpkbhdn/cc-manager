package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea

/** The inline note box under the last selected diff row; Ctrl+Enter adds, Esc cancels. */
@Composable
internal fun CommentEditor(text: TextFieldState, onAdd: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val add = { text.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onAdd) }
    Column(
        Modifier.fillMaxWidth().background(colors.sidebar).padding(Spacing.md)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onCancel(); true } else false },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        VoiceTextArea(text, Modifier.fillMaxWidth(), placeholder = "Comment for Claude (Ctrl+Enter to add)", focusRequester = focus, onSubmit = { add() })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppButton("Add comment", { add() }, variant = ButtonVariant.PRIMARY, compact = true)
            AppButton("Cancel", onCancel, variant = ButtonVariant.GHOST, compact = true)
        }
    }
}
