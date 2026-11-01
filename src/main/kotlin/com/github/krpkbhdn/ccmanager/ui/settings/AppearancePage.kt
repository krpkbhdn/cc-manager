package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode

private val themeSegments = listOf(
    Segment(ThemeMode.SYSTEM, "System", Icons.Monitor),
    Segment(ThemeMode.LIGHT, "Light", Icons.Sun),
    Segment(ThemeMode.DARK, "Dark", Icons.Moon),
)

@Composable
fun AppearancePage(state: AppState) {
    SettingGroup("Theme") {
        SettingRow("Color theme", "System follows the operating system's light or dark mode.") {
            SegmentedControl(themeSegments, state.settings.themeMode, state.prefs::setThemeMode)
        }
    }
    SettingGroup("Layout") {
        SettingRow("Collapse the sidebar", "Show only the icon rail on the left.") {
            AppSwitch(state.settings.sidebarCollapsed, { state.prefs.toggleSidebar() })
        }
    }
}
