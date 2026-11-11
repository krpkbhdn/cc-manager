package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp

/** The shared frame of the small author dialogs (report): modal card, sparkles header. */
@Composable
fun AuthorDialog(title: String, onClose: () -> Unit, width: Dp = dialogWidth, content: @Composable () -> Unit) {
    ModalHost(onClose, width = width) {
        DialogBody(title, Icons.Sparkles, colors.accent, content = content)
    }
}
