package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.awt.Robot
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SETTLE_MILLIS = 1_400L
private val previewSize = DpSize(1280.dp, 820.dp)

/**
 * Dev preview with fake services. Flags: `--light` (default dark), `--shots` captures the main states into `--out=<dir>`
 * with java.awt.Robot and exits; without `--shots` the window stays open for manual inspection.
 */
fun main(args: Array<String>) {
    val dark = "--light" !in args
    val shots = "--shots" in args
    val outDir = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: "build/preview")
    val world = PreviewWorld()
    application {
        val state = remember { AppState(world.backend(PreviewTerminal), CoroutineScope(SupervisorJob() + Dispatchers.Main)).also { it.start() } }
        Window(onCloseRequest = ::exitApplication, state = WindowState(size = previewSize), title = "Claude Code Manager (preview)") {
            AppTheme(dark) { AppRoot(state) }
            if (shots) {
                LaunchedEffect(Unit) {
                    window.isAlwaysOnTop = true
                    captureTour(Capture(window, outDir, if (dark) "dark" else "light"), state, world)
                    exitApplication()
                }
            }
        }
    }
}

private class Capture(val window: java.awt.Window, val dir: Path, val theme: String) {
    suspend fun take(name: String) {
        delay(SETTLE_MILLIS)
        withContext(Dispatchers.IO) {
            Files.createDirectories(dir)
            ImageIO.write(Robot().createScreenCapture(window.bounds), "png", dir.resolve("$theme-$name.png").toFile())
        }
    }
}

private suspend fun captureTour(capture: Capture, state: AppState, world: PreviewWorld) {
    capture.take("1-overview")
    state.openSession("s-needs")
    state.openSession("s-working")
    capture.take("2-project-terminal")
    state.select(1, ProjectTab.Diff)
    capture.take("3-diff")
    state.select(1, ProjectTab.Activity)
    capture.take("4-activity")
    state.notificationsOpen = true
    capture.take("5-notifications")
    state.notificationsOpen = false
    state.navigate(Route.Project(2))
    world.emit(AppNotification(99, Instant.now(), NotificationKind.NEEDS_ACTION, 1, "s-needs", "Needs your action", "Permission needed: Edit NotificationServiceTest.java"))
    capture.take("6-toast-other-project")
    state.requestQuit {}
    capture.take("7-quit-confirm")
    state.confirm = null
    world.claudeInstalled = false
    state.recheckInstallation()
    capture.take("8-install-missing")
}
