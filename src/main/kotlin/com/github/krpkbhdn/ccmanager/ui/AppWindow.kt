package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.slf4j.LoggerFactory
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
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
import java.awt.Desktop
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val WINDOW_TITLE = "Claude Code Manager"
private const val UI_ERROR_TITLE = "Unexpected error"
private const val TRAY_ICON_SIZE = 64
private const val WINDOW_ICON_SIZE = 256

/**
 * Shows the main window; [onShutdown] runs after the user confirmed quitting. [settingsStore] holds the remembered view state.
 * With a system tray, closing the window only hides it: sessions keep running and the tray icon brings the window back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.AppWindow(backend: Backend, onShutdown: () -> Unit, settingsStore: UiSettingsStore = UiSettingsStore.inDataDir(backend.dataDir)) {
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
    val handledRestores = remember { intArrayOf(0) }
    val restore: () -> Unit = { visible = true; windowState.isMinimized = false; restoreRequests++ }
    val exiting = remember { AtomicBoolean(false) }
    val exit = {
        if (exiting.compareAndSet(false, true)) {
            visible = false
            thread(name = "AppShutdown") {
                try {
                    TerminalHost.closeAll()
                    onShutdown()
                } finally {
                    SwingUtilities.invokeLater { exitApplication() }
                }
            }
        }
    }
    state.exitApp = exit
    val quit = { state.requestQuit(exit) }
    LaunchedEffect(Unit) { installMacQuitHandler({ state.live.sessions.isNotEmpty() }) { restore(); quit() } }
    if (isTraySupported) {
        val trayIcon = remember { BitmapPainter(drawAppIcon(TRAY_ICON_SIZE).toComposeImageBitmap()) }
        Tray(icon = trayIcon, tooltip = WINDOW_TITLE, onAction = restore) {
            Item("Open $WINDOW_TITLE", onClick = restore)
            Item("Quit", onClick = { restore(); quit() })
        }
    }
    var windowGeneration by remember { mutableIntStateOf(0) }
    val defaultHandlers = LocalWindowExceptionHandlerFactory.current
    val keepWindowAlive = remember { reportingExceptionHandlers(state, defaultHandlers, WindowRecovery()) { windowGeneration++ } }
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides keepWindowAlive) {
        key(windowGeneration) {
            MainWindow(state, windowState, WINDOW_TITLE, icon, if (isTraySupported) ({ visible = false }) else quit, visible) {
                val focused = LocalWindowInfo.current.isWindowFocused
                SideEffect { state.windowActive = focused && visible && !windowState.isMinimized }
                LaunchedEffect(restoreRequests) {
                    if (restoreRequests == handledRestores[0]) return@LaunchedEffect
                    handledRestores[0] = restoreRequests
                    window.isVisible = true
                    window.toFront()
                    window.requestFocus()
                }
            }
        }
    }
    ToastPopups(state.notifications.popups, primaryScreenArea(), isDarkTheme(state), { state.openToast(it); restore() }, state.notifications::dismissToast)
}

private fun visibleScreens(): List<ScreenArea> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map(::usableArea)

private fun primaryScreenArea(): ScreenArea = usableArea(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)

private fun usableArea(device: GraphicsDevice): ScreenArea {
    val bounds = device.defaultConfiguration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(device.defaultConfiguration)
    return ScreenArea(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
}

/** macOS Cmd+Q would otherwise call System.exit and skip the confirmation and the session shutdown. */
private fun installMacQuitHandler(sessionsRunning: () -> Boolean, onQuit: () -> Unit) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) return
    Desktop.getDesktop().setQuitHandler { _, response ->
        if (sessionsRunning()) {
            response.cancelQuit()
            SwingUtilities.invokeLater(onQuit)
        } else {
            response.performQuit()
        }
    }
}

/**
 * Compose's default handler shows a dialog and closes the window, which would end every running session. An exception kills
 * the window's recomposer, so [remount] rebuilds the window (app state and terminals live outside it) and a toast reports it;
 * when [recovery] refuses, the default handler takes over.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun reportingExceptionHandlers(state: AppState, fallback: WindowExceptionHandlerFactory, recovery: WindowRecovery, remount: () -> Unit): WindowExceptionHandlerFactory {
    var lastMessage: String? = null
    return WindowExceptionHandlerFactory { window ->
        WindowExceptionHandler { error ->
            LoggerFactory.getLogger(AppState::class.java).error("Unhandled UI exception", error)
            if (!recovery.tryRecover()) return@WindowExceptionHandler fallback.exceptionHandler(window).onException(error)
            remount()
            val message = error.message ?: error.javaClass.simpleName
            if (message != lastMessage) state.notifications.reportError(UI_ERROR_TITLE, message)
            lastMessage = message
        }
    }
}
