package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.shortcuts.ShortcutActions
import com.github.krpkbhdn.ccmanager.ui.shortcuts.ShortcutDispatcher
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.WindowBounds
import com.github.krpkbhdn.ccmanager.ui.terminal.terminalHasFocus
import org.jetbrains.jewel.window.DecoratedWindowScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val BOUNDS_SAVE_DELAY_MILLIS = 500L

/** Content of the main window: global shortcuts, remembered bounds, title bar (or top bar on the native frame) and the app itself. */
@Composable
fun FrameWindowScope.AppFrame(state: AppState, windowState: WindowState) {
    val systemDark = isSystemInDarkTheme()
    val currentSystemDark by rememberUpdatedState(systemDark)
    DisposableEffect(window, state) {
        val actions = ShortcutActions(state) { currentSystemDark }
        val installed = ShortcutDispatcher(window, terminalFocused = ::terminalHasFocus, perform = actions::perform).install()
        onDispose { installed.close() }
    }
    PersistWindowBounds(state, windowState)
    Column(Modifier.fillMaxSize()) {
        val frame = this@AppFrame
        if (frame is DecoratedWindowScope) frame.AppTitleBar(state) else TopBar(state)
        AppRoot(state, modifier = Modifier.weight(1f))
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PersistWindowBounds(state: AppState, windowState: WindowState) {
    LaunchedEffect(windowState) {
        snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
            .debounce(BOUNDS_SAVE_DELAY_MILLIS)
            .collect { (size, position, placement) ->
                state.updateSettings { it.copy(window = boundsOf(it.window, size, position, placement)) }
            }
    }
}

private fun boundsOf(saved: WindowBounds, size: DpSize, position: WindowPosition, placement: WindowPlacement): WindowBounds {
    if (placement != WindowPlacement.Floating) return saved.copy(maximized = placement == WindowPlacement.Maximized)
    val at = position.takeIf { it.isSpecified }
    return WindowBounds(size.width.value.toInt(), size.height.value.toInt(), at?.x?.value?.toInt(), at?.y?.value?.toInt())
}
