package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.limitsAt
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeRef
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.fetch
import com.github.krpkbhdn.ccmanager.ui.git.model.push
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat

private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 40
private const val POLL_MILLIS = 20L
private const val WAIT_MILLIS = 8_000L
private val defaultOut = "C:/Users/Bohdan/Work/Code/Personal/ccm-shots/git"

private class Shot(val name: String, val view: GitSubView = GitSubView.CHANGES, val data: () -> FakeRepoData = SampleRepos::busy, val prepare: (AppState, GitState, FakeRepoData) -> Unit = { _, _, _ -> }, val limits: RateLimitSnapshot? = limitsAt(23.5, 41.2))

private fun selectFile(path: String, staged: Boolean) = { _: AppState, git: GitState, _: FakeRepoData -> git.selected = ChangeRef(path, staged) }

private val shots = listOf(
    Shot("changes", prepare = selectFile("src/main/java/com/acme/SessionRegistry.java", false)),
    Shot("changes-staged", prepare = selectFile("src/main/java/com/acme/SessionSnapshot.java", true)),
    Shot("changes-amend", prepare = { s, git, _ -> selectFile("build.gradle.kts", false)(s, git, FakeRepoData()); git.draft.setAmend(true, "Add status service\n\nReads porcelain v2 and classifies failures.") }),
    Shot("changes-clean", data = SampleRepos::clean),
    Shot("log", GitSubView.LOG, prepare = { _, git, data -> git.log.reload(); waitFor { git.log.rows.isNotEmpty() }; git.log.select(data.rows[3].commit().hash()); waitFor { git.log.details != null } }),
    Shot("log-filter", GitSubView.LOG, prepare = { _, git, _ -> git.log.update { it.copy(author = "Priya") }; waitFor { git.log.rows.isNotEmpty() && !git.log.loading } }),
    Shot("branches", GitSubView.BRANCHES),
    Shot("branch-popup", prepare = { _, git, _ -> git.branchPopupOpen = true }),
    Shot("stashes", GitSubView.STASHES),
    Shot("tags", GitSubView.TAGS),
    Shot("merging", data = SampleRepos::merging, prepare = selectFile("src/main/java/com/acme/HookServer.java", false)),
    Shot("no-upstream", data = SampleRepos::noUpstream),
    Shot("identity-missing", data = SampleRepos::missingIdentity),
    Shot("not-a-repo", data = SampleRepos::notRepository),
    Shot("error-push", prepare = { _, git, data -> data.failure = GitException(GitException.Kind.NON_FAST_FORWARD, "Push rejected: the remote has newer commits", "! [rejected] main -> main (non-fast-forward)"); git.pushNow() }),
    Shot("error-auth", prepare = { _, git, data -> data.failure = GitException(GitException.Kind.AUTH, "Authentication failed for origin", "fatal: could not read Username for 'https://github.com'"); git.pushNow() }),
    Shot("dialog-new-branch", prepare = { _, git, _ -> git.dialog = GitDialog.NewBranch("main") }),
    Shot("dialog-dirty", prepare = { _, git, _ -> git.dialog = GitDialog.DirtyCheckout("release/1.0") }),
    Shot("dialog-reset", GitSubView.LOG, prepare = { _, git, data -> git.dialog = GitDialog.Reset(data.rows[2].commit().hash(), "${data.rows[2].commit().shortHash()} ${data.rows[2].commit().subject()}") }),
    Shot("dialog-hook", prepare = { _, git, _ -> git.dialog = GitDialog.Output("Commit hook failed", "pre-commit: lint failed\n  src/Main.java:12 unused import\n  src/Main.java:40 missing semicolon") }),
    Shot("overview", prepare = { state, _, _ -> state.navigate(Route.Overview) }),
    Shot("usage-high", limits = limitsAt(72.0, 93.4), prepare = { state, _, _ -> state.navigate(Route.Overview) }),
    Shot("usage-none", limits = null, prepare = { state, _, _ -> state.navigate(Route.Overview) }),
    Shot("shortcuts",prepare = { state, _, _ -> state.shortcutsOpen = true }),
    Shot("palette", prepare = { state, _, _ -> state.paletteOpen = true }),
    Shot("progress",prepare = { _, git, data -> data.progressDelayMillis = 4_000; git.fetchNow() }),
)

private fun GitState.pushNow() { push(); waitFor { error != null } }

private fun GitState.fetchNow() { fetch(); waitFor { job != null } }


private fun waitFor(timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
}

/**
 * Renders the Git tab offscreen with Compose's ImageComposeScene (no OS window, no input automation) for each scenario,
 * theme and size. `--out=<dir> --only=<name,name> --dark|--light --size=WxH`.
 */
fun main(args: Array<String>) {
    val out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: defaultOut)
    val only = args.firstOrNull { it.startsWith("--only=") }?.removePrefix("--only=")?.split(',')?.toSet()
    val themes = when { "--dark" in args -> listOf(true); "--light" in args -> listOf(false); else -> listOf(true, false) }
    val sizes = args.firstOrNull { it.startsWith("--size=") }?.removePrefix("--size=")?.split('x')?.map { it.toInt() }?.let { listOf(it[0] to it[1]) } ?: listOf(1280 to 820, 960 to 600)
    Files.createDirectories(out)
    shots.filter { only == null || it.name in only }.forEach { shot ->
        sizes.forEach { (w, h) -> themes.forEach { dark -> render(shot, dark, w, h, out) } }
    }
    kotlin.system.exitProcess(0)
}

private fun render(shot: Shot, dark: Boolean, width: Int, height: Int, out: Path) {
    val data = shot.data()
    val world = PreviewWorld(git = data, rateLimits = shot.limits)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val state = AppState(world.backend(PreviewTerminal), scope, UiSettingsStore(null)).also {
        it.start()
        it.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
    waitFor { state.projects.isNotEmpty() && state.installation != null }
    state.navigate(Route.Project(1))
    val git = state.openGit(1, shot.view)!!
    git.attach()
    waitFor { git.status != null && (git.status?.repository() == false || (git.branches.isNotEmpty() && git.stashes.isNotEmpty())) }
    shot.prepare(state, git, data)
    val scene = ImageComposeScene(width, height, Density(1f)) { AppTheme(dark) { AppRoot(state, osName = "Windows 11") } }
    try {
        var frame = 0L
        repeat(SETTLE_FRAMES) { scene.render(frame++ * FRAME_NANOS) }
        Thread.sleep(300)
        repeat(SETTLE_FRAMES) { scene.render(frame++ * FRAME_NANOS) }
        val bytes = scene.render(frame * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val file = out.resolve("${shot.name}-${if (dark) "dark" else "light"}-${width}x$height.png")
        Files.write(file, bytes)
        println("wrote $file")
    } finally {
        scene.close()
        scope.cancel()
    }
}
