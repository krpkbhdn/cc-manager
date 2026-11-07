package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.state.AppState

@Composable
fun NotificationsPage(state: AppState) {
    SettingGroup("Alerts") {
        SettingRow("Notification sound", "Play a sound when a session needs your attention.") {
            AppSwitch(state.settings.notificationSound, { state.prefs.toggleNotificationSound() })
        }
    }
    SettingGroup("Desktop popups") {
        SettingRow("Do not disturb", "Show no desktop popups.") {
            AppSwitch(state.settings.doNotDisturb, { state.prefs.toggleDoNotDisturb() })
        }
        SettingRow("Agent needs your action", "A question, plan, permission or form is waiting.") {
            AppSwitch(state.settings.osNotifyNeedsAction, { state.prefs.toggleOsNotifyNeedsAction() })
        }
        SettingRow("Session finished", "A session finished its turn.") {
            AppSwitch(state.settings.osNotifyFinished, { state.prefs.toggleOsNotifyFinished() })
        }
        SettingRow("Session error", "A session failed.") {
            AppSwitch(state.settings.osNotifyError, { state.prefs.toggleOsNotifyError() })
        }
        if (state.trayAvailable) {
            SettingRow("Minimize to tray", "Closing the window keeps sessions running in the tray.") {
                AppSwitch(state.settings.minimizeToTray, { state.prefs.toggleMinimizeToTray() })
            }
        } else {
            SettingRow("Tray unavailable", "This system has no tray, so minimizing to tray is off.") {}
        }
    }
}
