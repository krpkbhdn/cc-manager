package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.RadioMark
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private fun AssistantStartup.description(): String = when (this) {
    AssistantStartup.ASK -> "Offer to start the Assistant each time the app opens."
    AssistantStartup.AUTO_START -> "Start the Assistant in the background when the app opens."
    AssistantStartup.NEVER -> "Do not start the Assistant on its own."
}

@Composable
fun AssistantPage(state: AppState) {
    SettingGroup("At app start") {
        AssistantStartup.entries.forEach { mode ->
            StartupChoice(mode, state.settings.assistantStartup == mode) { state.setAssistantStartup(mode) }
        }
    }
    SettingGroup("Now") {
        SettingRow("Start the Assistant", "Open the Assistant session outside any project, or switch to it when it is running.") {
            AppButton("Start now", state.assistant::start)
        }
    }
}

@Composable
private fun StartupChoice(mode: AssistantStartup, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RadioMark(selected)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(mode.label, style = Type.bodyMedium)
                Label(mode.description(), color = colors.textSecondary, style = Type.small)
            }
        }
    }
}
