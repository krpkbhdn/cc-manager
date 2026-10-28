package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.state.AppState

@Composable
fun NotificationsPage(state: AppState) {
    SettingGroup("Alerts") {
        SettingRow("Notification sound", "Play a sound when a session needs your attention.") {
            AppSwitch(state.settings.notificationSound, { state.toggleNotificationSound() })
        }
    }
}
