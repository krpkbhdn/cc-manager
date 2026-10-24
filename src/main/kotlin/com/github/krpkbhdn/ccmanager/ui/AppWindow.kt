package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme

import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val WINDOW_TITLE = "Claude Code Manager"
private val defaultWindowSize = DpSize(1280.dp, 820.dp)
private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 600
private const val TRAY_ICON_SIZE = 64
private const val TRAY_ICON_MARGIN = 6
private const val TRAY_BACKGROUND = 0xFF3C6DF0.toInt()
private const val TRAY_GLYPH_STROKE = 5f

/** Shows the main window; [onShutdown] runs after the user confirmed quitting. [dark] forces a theme (previews). */
@Composable
fun ApplicationScope.AppWindow(backend: Backend, onShutdown: () -> Unit, windowState: WindowState = WindowState(size = defaultWindowSize), dark: Boolean? = null) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val state = remember { AppState(backend, scope).also { it.start() } }
    DisposableEffect(Unit) { onDispose { state.dispose(); scope.cancel() } }
    val trayState = rememberTrayState()
    if (isTraySupported) TrayIcon(state, trayState)
    Window(
        onCloseRequest = { state.requestQuit { onShutdown(); exitApplication() } },
        state = windowState,
        title = WINDOW_TITLE,
    ) {
        window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        AppTheme(dark ?: isSystemInDarkTheme()) { AppRoot(state) }
    }
}

@Composable
private fun ApplicationScope.TrayIcon(state: AppState, trayState: TrayState) {
    val icon = remember { BitmapPainter(drawTrayIcon().toComposeImageBitmap()) }
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

/** Rounded blue square with a terminal prompt, drawn so no image resource is needed. */
private fun drawTrayIcon(): BufferedImage {
    val image = BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = java.awt.Color(TRAY_BACKGROUND)
    g.fillRoundRect(0, 0, TRAY_ICON_SIZE, TRAY_ICON_SIZE, TRAY_ICON_SIZE / 4, TRAY_ICON_SIZE / 4)
    g.color = java.awt.Color.WHITE
    g.stroke = java.awt.BasicStroke(TRAY_GLYPH_STROKE, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
    val m = TRAY_ICON_SIZE / 4
    g.drawPolyline(intArrayOf(m, TRAY_ICON_SIZE / 2 - TRAY_ICON_MARGIN, m), intArrayOf(m + 2, TRAY_ICON_SIZE / 2, TRAY_ICON_SIZE - m - 2), 3)
    g.drawLine(TRAY_ICON_SIZE / 2, TRAY_ICON_SIZE - m - 2, TRAY_ICON_SIZE - m, TRAY_ICON_SIZE - m - 2)
    g.dispose()
    return image
}
