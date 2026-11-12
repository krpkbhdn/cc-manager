package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Chooses which live session of the project receives a review's feedback; null from [onPick] means a new session. */
@Composable
fun SessionTargetDialog(candidates: List<SessionChoice>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalHost(onDismiss) {
        DialogBody("Send to agent", Icons.Send, colors.accent) {
            if (candidates.isNotEmpty()) Label("Send to a running session", color = colors.textSecondary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                candidates.forEach { AppButton(it.label, { onPick(it.ccmSessionId) }, Modifier.fillMaxWidth(), icon = Icons.Bot) }
                AppButton("New session", { onPick(null) }, Modifier.fillMaxWidth(), variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
                AppButton("Cancel", onDismiss, Modifier.fillMaxWidth(), variant = ButtonVariant.GHOST)
            }
        }
    }
}
