package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.state.AppState

@Composable
fun AuthorPage(state: AppState) {
    SettingGroup("Claude setup") {
        SettingRow(
            "Author configuration",
            "The author's Claude Code setup: marketplaces, plugins, skills, MCP servers, settings and agent teams. Install it once or update it later.",
        ) {
            AppButton("Import / update...", { state.author.openManage() })
        }
    }
}
