package com.github.krpkbhdn.ccmanager.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Asks at app start whether to start the Assistant; Esc and click outside only close it. */
@Composable
fun AssistantStartupDialog(state: AssistantStartupState) {
    ModalHost(state::dismiss) {
        DialogBody(
            "Start the Assistant?", Icons.Sparkles, colors.accent,
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                    AppButton("Not now", { state.choose(false) })
                    AppButton("Start", { state.choose(true) }, variant = ButtonVariant.PRIMARY)
                }
            },
        ) {
            Label("A Claude session for questions unrelated to your projects.", color = colors.textSecondary, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppCheckbox(state.dontAskAgain, { state.dontAskAgain = it })
                Label("Don't ask again")
            }
        }
    }
}
