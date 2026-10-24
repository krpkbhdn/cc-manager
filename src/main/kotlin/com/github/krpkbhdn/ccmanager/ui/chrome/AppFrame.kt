package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.WindowBounds
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val BOUNDS_SAVE_DELAY_MILLIS = 500L

/** Everything inside the undecorated window: themed title bar, app content and edge resize handles. */
@OptIn(FlowPreview::class)
@Composable
fun FrameWindowScope.AppFrame(state: AppState, windowState: WindowState, onClose: () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val controls = remember(windowState, onClose) { windowControls(windowState, onClose) { window } }
    LaunchedEffect(windowState) {
        snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
            .debounce(BOUNDS_SAVE_DELAY_MILLIS)
            .collect { (size, position, placement) ->
                if (placement == WindowPlacement.Floating) {
                    val at = position.takeIf { it.isSpecified }
                    state.updateSettings { it.copy(window = WindowBounds(size.width.value.toInt(), size.height.value.toInt(), at?.x?.value?.toInt(), at?.y?.value?.toInt())) }
                }
            }
    }
    AppTheme(dark) {
        Box(Modifier.fillMaxSize().border(1.dp, colors.borderStrong)) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(state, controls.copy(maximized = windowState.placement == WindowPlacement.Maximized), Modifier.windowDrag(window, windowState.placement != WindowPlacement.Maximized))
                AppRoot(state, modifier = Modifier.weight(1f))
            }
            ResizeFrame(window, enabled = windowState.placement != WindowPlacement.Maximized)
        }
    }
}

private fun windowControls(windowState: WindowState, onClose: () -> Unit, window: () -> java.awt.Window): WindowControls =
    WindowControls(
        minimize = { windowState.isMinimized = true },
        toggleMaximize = {
            if (windowState.placement == WindowPlacement.Maximized) {
                windowState.placement = WindowPlacement.Floating
            } else {
                (window() as? java.awt.Frame)?.maximizedBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                windowState.placement = WindowPlacement.Maximized
            }
        },
        close = onClose,
        maximized = false,
    )

private fun WindowControls.copy(maximized: Boolean) = WindowControls(minimize, toggleMaximize, close, maximized)
