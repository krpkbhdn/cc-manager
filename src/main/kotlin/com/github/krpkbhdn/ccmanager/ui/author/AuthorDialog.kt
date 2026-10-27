package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp

/** The shared frame of the author dialogs: modal card, sparkles header. */
@Composable
fun AuthorDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    ModalHost(onClose, width = dialogWidth) {
        DialogBody(title, Icons.Sparkles, colors.accent, content = content)
    }
}

/** Shown under a team update, which overwrites the user's own roles. */
@Composable
fun ReplaceWarning(entry: AuthorEntry) {
    if (replacesEdits(entry)) Label(TEAM_REPLACE_WARNING, color = colors.warning, style = Type.caption, maxLines = 3)
}

/** What an update brings: the all-at-once hint and the changes, one line each. */
@Composable
fun ChangeList(entry: AuthorEntry) {
    if (entry.status() == AuthorStatus.UPDATE_AVAILABLE) Label(UPDATE_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 2)
    entry.changes().forEach { Label("- $it", color = colors.textSecondary, style = Type.caption, maxLines = 2) }
}
