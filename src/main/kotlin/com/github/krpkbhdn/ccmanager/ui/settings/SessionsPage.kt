package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.state.AppState

@Composable
fun SessionsPage(state: AppState) {
    SettingGroup("All sessions") {
        SettingRow("Group by project", "List the sessions of the All sessions screen under their project.") {
            AppSwitch(state.settings.groupSessionsByProject, state.prefs::setGroupSessionsByProject)
        }
    }
}
