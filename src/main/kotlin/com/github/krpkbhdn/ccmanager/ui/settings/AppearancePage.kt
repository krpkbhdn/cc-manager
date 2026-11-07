package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings

private val themeSegments = listOf(
    Segment(ThemeMode.SYSTEM, "System", Icons.Monitor),
    Segment(ThemeMode.LIGHT, "Light", Icons.Sun),
    Segment(ThemeMode.DARK, "Dark", Icons.Moon),
)

private const val SCALE_STEP_PERCENT = 10

@Composable
fun AppearancePage(state: AppState) {
    SettingGroup("Theme") {
        SettingRow("Color theme", "System follows the operating system's light or dark mode.") {
            SegmentedControl(themeSegments, state.settings.themeMode, state.prefs::setThemeMode)
        }
    }
    SettingGroup("Scale") {
        SettingRow("Interface scale", "Size of everything in the app. Raise it on a 4K monitor.") {
            PercentStepper(state.settings.uiScalePercent, UiSettings.UI_SCALE_RANGE.first, UiSettings.UI_SCALE_RANGE.last, SCALE_STEP_PERCENT, state.prefs::setUiScalePercent)
        }
        SettingRow("Font scale", "Extra multiplier for text only, on top of the interface scale.") {
            PercentStepper(state.settings.fontScalePercent, UiSettings.FONT_SCALE_RANGE.first, UiSettings.FONT_SCALE_RANGE.last, SCALE_STEP_PERCENT, state.prefs::setFontScalePercent)
        }
    }
    SettingGroup("Layout") {
        SettingRow("Collapse the sidebar", "Show only the icon rail on the left.") {
            AppSwitch(state.settings.sidebarCollapsed, { state.prefs.toggleSidebar() })
        }
    }
}
