package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route

@Composable
fun AuthorPage(state: AppState) {
    SettingGroup("Claude setup") {
        SettingRow(
            "Author configuration",
            "The author's Claude Code setup: marketplaces, plugins, skills, MCP servers and agent teams, plus CLAUDE.md and settings for a project. Install it once or update it later.",
        ) {
            AppButton("Import / update...", { state.author.openManage((state.route as? Route.Project)?.id) })
        }
    }
}
