package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.chrome.MainWindow
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
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

private const val SETTLE_MILLIS = 1_600L

/**
 * Dev preview with fake services. Flags: `--light` (default dark), `--many` (12 projects, long names), `--empty`,
 * `--size=WxH`, `--shots` captures the main states into `--out=<dir>` with java.awt.Robot and exits; without
 * `--shots` the window stays open for manual inspection. `--real` boots the real stack instead (see RealMain).
 */
fun main(args: Array<String>) {
    if ("--real" in args) return runReal(args)
    val flags = PreviewFlags.parse(args)
    val world = PreviewWorld(many = flags.many, empty = flags.empty)
    application {
        val state = remember {
            AppState(world.backend(PreviewTerminal), CoroutineScope(SupervisorJob() + Dispatchers.Main), UiSettingsStore(null))
                .also { it.start(); it.setThemeMode(if (flags.dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        }
        val windowState = remember { WindowState(size = DpSize(flags.width.dp, flags.height.dp)) }
        val icon = remember { BitmapPainter(drawAppIcon(256).toComposeImageBitmap()) }
        MainWindow(state, windowState, "Claude Code Manager (preview)", icon, ::exitApplication) {
            if (flags.snap) LaunchedEffect(Unit) {
                window.isAlwaysOnTop = true
                windowProbe(window, windowState, Capture(window, flags.out, "native"), flags.out.resolve("window.txt"))
                exitApplication()
            }
            if (flags.shots) {
                LaunchedEffect(Unit) {
                    window.isAlwaysOnTop = true
                    captureTour(Capture(window, flags.out, "${if (flags.dark) "dark" else "light"}-${flags.width}"), state, world, flags)
                    exitApplication()
                }
            }
        }
    }
}

private class PreviewFlags(val dark: Boolean, val shots: Boolean, val snap: Boolean, val many: Boolean, val empty: Boolean, val width: Int, val height: Int, val out: Path) {
    companion object {
        fun parse(args: Array<String>): PreviewFlags {
            val size = args.firstOrNull { it.startsWith("--size=") }?.removePrefix("--size=")?.split('x')?.map { it.toInt() } ?: listOf(1280, 820)
            return PreviewFlags(
                dark = "--light" !in args,
                shots = "--shots" in args,
                snap = "--snap" in args,
                many = "--many" in args,
                empty = "--empty" in args,
                width = size[0],
                height = size[1],
                out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: "build/preview"),
            )
        }
    }
}

internal class Capture(val window: java.awt.Window, val dir: Path, val prefix: String) {
    suspend fun take(name: String, settleMillis: Long = SETTLE_MILLIS) {
        delay(settleMillis)
        withContext(Dispatchers.IO) {
            Files.createDirectories(dir)
            ImageIO.write(Robot().createScreenCapture(window.bounds), "png", dir.resolve("$prefix-$name.png").toFile())
        }
    }
}

private suspend fun captureTour(capture: Capture, state: AppState, world: PreviewWorld, flags: PreviewFlags) {
    if (flags.empty) {
        capture.take("0-empty")
        return
    }
    capture.take("1-overview")
    state.navigate(Route.Sessions)
    capture.take("1b-all-sessions")
    state.setGroupSessionsByProject(true)
    capture.take("1c-all-sessions-grouped")
    state.setGroupSessionsByProject(false)
    state.navigate(Route.Overview)
    state.openSession("s-needs")
    state.openSession("s-working")
    capture.take("2-project-terminal")
    if (flags.many) {
        (1..8).forEach { state.openSession("s-crowd-$it") }
        capture.take("2b-many-tabs")
    }
    state.select(1, ProjectTab.Git)
    capture.take("3-diff")
    state.select(1, ProjectTab.Activity)
    capture.take("4-activity")
    state.select(1, ProjectTab.Run)
    capture.take("4a-run-tab")
    state.runs.select(1, 2)
    capture.take("4b-run-exited")
    state.runs.openDialog(1, state.runs.selectedOf(1)?.configuration)
    capture.take("4c-run-dialog")
    state.runs.closeDialog()
    state.notificationsOpen = true
    capture.take("5-notifications")
    state.notificationsOpen = false
    state.paletteOpen = true
    capture.take("6-palette")
    state.paletteOpen = false
    state.navigate(Route.Project(2))
    world.emit(AppNotification(99, Instant.now(), NotificationKind.NEEDS_ACTION, 1, "s-needs", "Needs your action", "Permission needed: Edit NotificationServiceTest.java"))
    capture.take("7-toast-other-project")
    state.toggleSidebar()
    state.toggleAgents()
    capture.take("8-collapsed")
    state.toggleSidebar()
    state.toggleAgents()
    state.requestQuit {}
    capture.take("9-quit-confirm")
    state.confirm = null
    world.claudeInstalled = false
    state.recheckInstallation()
    capture.take("10-install-missing")
}
