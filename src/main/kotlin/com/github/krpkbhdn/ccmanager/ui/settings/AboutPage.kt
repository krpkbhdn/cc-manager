package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.state.AppState

@Composable
fun AboutPage(state: AppState) {
    SettingGroup("Claude Code Manager") {
        SettingRow("Version", state.update.currentVersion.ifBlank { "Not a release build" }) {}
        SettingRow("Releases", "Release notes and installers on GitHub.") {
            AppButton("Open releases", state.update::openReleases, enabled = state.update.enabled)
        }
    }
    state.environment.installation?.takeIf { it.installed }?.let { claude ->
        SettingGroup("Claude Code") {
            SettingRow("Installed version", claude.version.orEmpty().ifBlank { "Unknown" }) {}
            SettingRow("Login", claude.login.name.lowercase().replace('_', ' ')) {}
        }
    }
}
