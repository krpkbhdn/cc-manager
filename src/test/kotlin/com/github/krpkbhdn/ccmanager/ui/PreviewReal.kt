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
import com.github.krpkbhdn.ccmanager.CcManagerApplication
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.chrome.MainWindow
import com.github.krpkbhdn.ccmanager.ui.git.GitDemoRepo
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private val sandbox = Path.of("C:/Users/Bohdan/Work/Code/Personal/ccm-sandbox")
private const val POLL_MILLIS = 500L
private const val CLAUDE_BOOT_MILLIS = 9_000L
private const val TRUST_SETTLE_MILLIS = 5_000L
private const val TRUST_FOLDER_KEYS = "\u001b[B\r"

/**
 * `runPreview --args="--real [--light] [--size=WxH] [--out=dir]"`: boots the real Spring context on a temporary data
 * directory, adds the sandbox projects, launches real Claude sessions and captures the UI. At most two tiny prompts are sent.
 */
internal fun runReal(args: Array<String>) {
    val dark = "--light" !in args
    val size = args.firstOrNull { it.startsWith("--size=") }?.removePrefix("--size=")?.split('x')?.map { it.toInt() } ?: listOf(1280, 820)
    val out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: "build/real")
    val data = Files.createTempDirectory("ccm-real")
    val context = CcManagerApplication.start(arrayOf("--cc-manager.data-dir=$data"))
    val backend = Backend.from(context)
    TerminalHost.configure(backend.sessions)
    application {
        val state = remember {
            AppState(backend, CoroutineScope(SupervisorJob() + Dispatchers.Main), UiSettingsStore(null))
                .also { it.start(); it.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        }
        val windowState = remember { WindowState(size = DpSize(size[0].dp, size[1].dp)) }
        val icon = remember { BitmapPainter(drawAppIcon(256).toComposeImageBitmap()) }
        MainWindow(state, windowState, "Claude Code Manager (real)", icon, ::exitApplication) {
            LaunchedEffect(Unit) {
                window.isAlwaysOnTop = true
                val capture = Capture(window, out, "real-${if (dark) "dark" else "light"}-${size[0]}")
                when {
                    "--shortcuts" in args -> shortcutProbe(capture, state, out.resolve("shortcuts.txt"))
                    "--attention" in args -> attentionTour(capture, state)
                    "--git" in args -> gitTour(capture, state)
                    else -> realTour(capture, state, prompts = "--noprompt" !in args)
                }
                backend.sessions.sessions().forEach { TerminalHost.close(it.ccmSessionId()) }
                context.close()
                exitApplication()
            }
        }
    }
}

private suspend fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMillis) { while (!condition()) delay(POLL_MILLIS); true } ?: false

private fun statuses(state: AppState) = state.live.sessions.map { it.status }

private suspend fun realTour(capture: Capture, state: AppState, prompts: Boolean) {
    capture.take("0-empty")
    state.requestAddProject()
    capture.take("1-picker-placeholder")
    listOf("demo-api", "demo-web", "notes").forEach { name ->
        state.addProjectAt(sandbox.resolve(name))
        delay(900)
    }
    state.navigate(com.github.krpkbhdn.ccmanager.ui.state.Route.Overview)
    capture.take("2-overview-three-projects")
    val api = state.projects.first { it.name == "demo-api" }
    val web = state.projects.first { it.name == "demo-web" }
    state.launch(api.id, LaunchMode.NEW)
    delay(CLAUDE_BOOT_MILLIS)
    capture.take("3-demo-api-terminal")
    val typedSession = state.live.of(api.id).first().ccmSessionId
    state.send(typedSession, "typed via the terminal host ✓ ї")
    delay(QUICK_SHOT_MILLIS)
    capture.take("3b-typed-echo")
    state.send(typedSession, CLEAR_INPUT_LINE)
    state.launch(web.id, LaunchMode.NEW)
    delay(CLAUDE_BOOT_MILLIS)
    capture.take("4-demo-web-terminal")
    val webSession = state.live.of(web.id).first().ccmSessionId
    val apiSession = state.live.of(api.id).first().ccmSessionId
    if (prompts) {
        state.send(webSession, TRUST_FOLDER_KEYS)
        delay(TRUST_SETTLE_MILLIS)
    }
    if (prompts) {
        state.send(webSession, "say hi in two words\r")
        waitUntil(20_000) { AgentStatus.WORKING in statuses(state) }
        capture.take("5-working")
        waitUntil(60_000) { state.live.find(webSession)?.status == AgentStatus.IDLE }
    }
    state.navigate(com.github.krpkbhdn.ccmanager.ui.state.Route.Overview)
    capture.take("6-overview-after-reply")
    state.openSession(apiSession)
    if (prompts) {
        state.send(apiSession, "create a file x.txt containing hi\r")
        waitUntil(90_000) { state.live.find(apiSession)?.status == AgentStatus.NEEDS_ACTION }
    }
    capture.take("7-needs-action-terminal")
    state.paletteOpen = true
    capture.take("8-palette-over-terminal")
    state.paletteOpen = false
    state.notificationsOpen = true
    capture.take("9-notifications-over-terminal")
    state.notificationsOpen = false
    state.navigate(com.github.krpkbhdn.ccmanager.ui.state.Route.Overview)
    capture.take("10-overview-needs-you")
    state.openSession(apiSession)
    if (prompts) {
        state.send(apiSession, "\r")
        waitUntil(60_000) { state.live.find(apiSession)?.status == AgentStatus.IDLE }
    }
    capture.take("11-after-approval")
    state.select(api.id, ProjectTab.Git)
    capture.take("12-real-diff")
    state.select(api.id, ProjectTab.Activity)
    capture.take("13-real-activity")
}

private const val GIT_LOAD_MILLIS = 2_500L

/** `--real --git`: adds the throwaway ccm-sandbox/git-demo repository and shoots every Git section against the real git services. */
private suspend fun gitTour(capture: Capture, state: AppState) {
    val repo = GitDemoRepo.create(sandbox)
    state.addProjectAt(repo)
    delay(GIT_LOAD_MILLIS)
    val project = state.projects.first { it.path == repo }
    GitSubView.entries.forEach { view ->
        state.openGit(project.id, view)
        delay(GIT_LOAD_MILLIS)
        capture.take("git-${view.name.lowercase()}")
    }
}

private const val SHIFT_TAB = "\u001b[Z"
private const val QUICK_SHOT_MILLIS = 250L
private const val CLEAR_INPUT_LINE = "\u0015"

/** One trusted session, plan mode via Shift+Tab, one prompt: shows working (yellow), then the plan approval (red). */
private suspend fun attentionTour(capture: Capture, state: AppState) {
    state.addProjectAt(sandbox.resolve("demo-api"))
    delay(900)
    val api = state.projects.first { it.name == "demo-api" }
    state.launch(api.id, LaunchMode.NEW)
    delay(CLAUDE_BOOT_MILLIS)
    val session = state.live.of(api.id).first().ccmSessionId
    repeat(3) { index ->
        state.send(session, SHIFT_TAB)
        capture.take("a${index + 1}-mode-cycle")
    }
    state.send(session, "plan how to add a file z.txt containing hi, in one line\r")
    waitUntil(20_000) { state.live.find(session)?.status == AgentStatus.WORKING }
    capture.take("a4-working", QUICK_SHOT_MILLIS)
    state.navigate(com.github.krpkbhdn.ccmanager.ui.state.Route.Overview)
    capture.take("a5-overview-working", QUICK_SHOT_MILLIS)
    waitUntil(120_000) { state.live.find(session)?.status != AgentStatus.WORKING }
    capture.take("a6-after-work", QUICK_SHOT_MILLIS)
    state.openSession(session)
    capture.take("a7-terminal-after-work")
    state.navigate(com.github.krpkbhdn.ccmanager.ui.state.Route.Overview)
    capture.take("a8-overview-attention")
    state.paletteOpen = true
    capture.take("a9-palette")
    state.paletteOpen = false
    state.openSession(session)
    state.send(session, "\u001b")
    waitUntil(20_000) { state.live.find(session)?.status == AgentStatus.IDLE }
    capture.take("a10-after-escape")
}
