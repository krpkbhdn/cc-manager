package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.awt.Dimension
import org.jetbrains.jewel.window.DecoratedWindow

private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 600

/** The app window: JetBrainsRuntime custom decorations when available (native snap, maximize, resize), the plain OS frame otherwise. */
@Composable
fun ApplicationScope.MainWindow(
    state: AppState,
    windowState: WindowState,
    title: String,
    icon: Painter,
    onCloseRequest: () -> Unit,
    visible: Boolean = true,
    extra: @Composable FrameWindowScope.() -> Unit = {},
) {
    val dark = isDarkTheme(state)
    val content: @Composable FrameWindowScope.() -> Unit = {
        window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        AppFrame(state, windowState)
        extra()
    }
    AppTheme(dark) {
        if (WindowDecorationSupport.available) {
            DecoratedWindow(onCloseRequest = onCloseRequest, state = windowState, visible = visible, title = title, icon = icon, content = content)
        } else {
            Window(onCloseRequest = onCloseRequest, state = windowState, visible = visible, title = title, icon = icon, content = content)
        }
    }
}

/** Resolves the user's theme choice, following the OS for [ThemeMode.SYSTEM]. */
@Composable
fun isDarkTheme(state: AppState): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (state.settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}
