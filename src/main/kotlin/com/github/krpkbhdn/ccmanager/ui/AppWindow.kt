package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.isTraySupported
import com.github.krpkbhdn.ccmanager.ui.chrome.MainWindow
import com.github.krpkbhdn.ccmanager.ui.chrome.isDarkTheme
import com.github.krpkbhdn.ccmanager.ui.components.ToastPopups
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.state.ScreenArea
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.ui.state.fitToScreens
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val WINDOW_TITLE = "Claude Code Manager"
private const val TRAY_ICON_SIZE = 64
private const val WINDOW_ICON_SIZE = 256

/**
 * Shows the main window; [onShutdown] runs after the user confirmed quitting. [settingsStore] holds the remembered view state.
 * With a system tray, closing the window only hides it: sessions keep running and the tray icon brings the window back.
 */
@Composable
fun ApplicationScope.AppWindow(backend: Backend, onShutdown: () -> Unit, settingsStore: UiSettingsStore = UiSettingsStore.inUserHome()) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val state = remember { AppState(backend, scope, settingsStore).also { it.start() } }
    DisposableEffect(Unit) { onDispose { state.dispose(); scope.cancel() } }
    val icon = remember { BitmapPainter(drawAppIcon(WINDOW_ICON_SIZE).toComposeImageBitmap()) }
    val bounds = remember { fitToScreens(state.settings.window, visibleScreens()) }
    val windowState = remember {
        WindowState(
            placement = if (bounds.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(bounds.width.dp, bounds.height.dp),
            position = if (bounds.x != null && bounds.y != null) WindowPosition(bounds.x.dp, bounds.y.dp) else WindowPosition.PlatformDefault,
        )
    }
    var visible by remember { mutableStateOf(true) }
    var restoreRequests by remember { mutableIntStateOf(0) }
    val restore: () -> Unit = { visible = true; windowState.isMinimized = false; restoreRequests++ }
    val exit = { onShutdown(); exitApplication() }
    state.exitApp = exit
    val quit = { state.requestQuit(exit) }
    if (isTraySupported) {
        val trayIcon = remember { BitmapPainter(drawAppIcon(TRAY_ICON_SIZE).toComposeImageBitmap()) }
        Tray(icon = trayIcon, tooltip = WINDOW_TITLE, onAction = restore) {
            Item("Open $WINDOW_TITLE", onClick = restore)
            Item("Quit", onClick = { restore(); quit() })
        }
    }
    MainWindow(state, windowState, WINDOW_TITLE, icon, if (isTraySupported) ({ visible = false }) else quit, visible) {
        val focused = LocalWindowInfo.current.isWindowFocused
        SideEffect { state.windowActive = focused && visible && !windowState.isMinimized }
        LaunchedEffect(restoreRequests) {
            if (restoreRequests == 0) return@LaunchedEffect
            window.isVisible = true
            window.toFront()
            window.requestFocus()
        }
    }
    ToastPopups(state.popups, primaryScreenArea(), isDarkTheme(state), { state.openToast(it); restore() }, state::dismissToast)
}

private fun visibleScreens(): List<ScreenArea> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map(::usableArea)

private fun primaryScreenArea(): ScreenArea = usableArea(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)

private fun usableArea(device: GraphicsDevice): ScreenArea {
    val bounds = device.defaultConfiguration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(device.defaultConfiguration)
    return ScreenArea(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
}
