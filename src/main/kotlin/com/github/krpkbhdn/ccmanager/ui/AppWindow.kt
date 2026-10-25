package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.chrome.MainWindow
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.state.ScreenArea
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.ui.state.fitToScreens
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val WINDOW_TITLE = "Claude Code Manager"
private const val TRAY_ICON_SIZE = 64
private const val WINDOW_ICON_SIZE = 256

/** Shows the main window; [onShutdown] runs after the user confirmed quitting. [settingsStore] holds the remembered view state. */
@Composable
fun ApplicationScope.AppWindow(backend: Backend, onShutdown: () -> Unit, settingsStore: UiSettingsStore = UiSettingsStore.inUserHome()) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val state = remember { AppState(backend, scope, settingsStore).also { it.start() } }
    DisposableEffect(Unit) { onDispose { state.dispose(); scope.cancel() } }
    val trayState = rememberTrayState()
    val icon = remember { BitmapPainter(drawAppIcon(WINDOW_ICON_SIZE).toComposeImageBitmap()) }
    if (isTraySupported) TrayIcon(state, trayState)
    val bounds = remember { fitToScreens(state.settings.window, visibleScreens()) }
    val windowState = remember {
        WindowState(
            placement = if (bounds.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(bounds.width.dp, bounds.height.dp),
            position = if (bounds.x != null && bounds.y != null) WindowPosition(bounds.x.dp, bounds.y.dp) else WindowPosition.PlatformDefault,
        )
    }
    val exit = { onShutdown(); exitApplication() }
    state.exitApp = exit
    val quit = { state.requestQuit(exit) }
    MainWindow(state, windowState, WINDOW_TITLE, icon, quit)
}

private fun visibleScreens(): List<ScreenArea> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        val bounds = device.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(device.defaultConfiguration)
        ScreenArea(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
    }

@Composable
private fun ApplicationScope.TrayIcon(state: AppState, trayState: TrayState) {
    val icon = remember { BitmapPainter(drawAppIcon(TRAY_ICON_SIZE).toComposeImageBitmap()) }
    LaunchedEffect(Unit) { state.notificationEvents.collect { trayState.sendNotification(osNotification(state, it)) } }
    Tray(state = trayState, icon = icon, tooltip = WINDOW_TITLE)
}

private fun osNotification(state: AppState, notification: AppNotification): Notification {
    val type = when (notification.kind) {
        NotificationKind.ERROR -> Notification.Type.Error
        NotificationKind.NEEDS_ACTION -> Notification.Type.Warning
        else -> Notification.Type.Info
    }
    return Notification("${state.projectName(notification.projectId)}: ${notification.title}", notification.body, type)
}
