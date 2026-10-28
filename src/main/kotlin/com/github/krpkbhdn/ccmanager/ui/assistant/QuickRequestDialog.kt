package com.github.krpkbhdn.ccmanager.ui.assistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp

/** A one-shot request to the Assistant: Enter sends, Shift+Enter adds a line. */
@Composable
fun QuickRequestDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val text = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val send = { if (text.text.isNotBlank()) onSend(text.text.toString()) }
    val enterSends = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
        val plainEnter = event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed
        if (plainEnter) send()
        plainEnter
    }
    ModalHost(onDismiss, width = dialogWidth, takeFocus = false) {
        DialogBody("Ask assistant", Icons.Sparkles, colors.accent, actions = { DialogActions("Send", text.text.isNotBlank(), false, send, onDismiss) }) {
            AppTextArea(text, enterSends, placeholder = "What should the assistant do? Enter sends, Shift+Enter adds a line", focusRequester = focus, minLines = 3, maxLines = 10, onSubmit = send)
        }
    }
}
