package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.chrome.TopBar
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.project.ModelSelection
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
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import com.github.krpkbhdn.ccmanager.ui.proposal.QuestionsDraft
import com.github.krpkbhdn.ccmanager.ui.screens.AppRoot
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.teams.RoleForm
import com.github.krpkbhdn.ccmanager.ui.teams.ToolSelection
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.ui.NEEDS_COMMAND_PLUGIN
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.DetailsState
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.HealthCheck
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.RawDetails
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.mcpKey
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillText
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.ExtensionFailure
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsTab
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsSection
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsState
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.key
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat

private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 40
private val CLOSE_FRAMES = listOf(1L, 4L, 10L, 40L)
private const val POLL_MILLIS = 20L
private const val SETTLE_MILLIS = 300L
private const val WAIT_MILLIS = 8_000L
private val defaultOut = "C:/Users/Bohdan/Work/Code/Personal/ccm-shots/git"

private class Shot(val name: String, val view: GitSubView = GitSubView.CHANGES, val data: () -> FakeRepoData = SampleRepos::busy, val prepare: (AppState, GitState, FakeRepoData) -> Unit = { _, _, _ -> }, val limits: RateLimitSnapshot? = limitsAt(23.5, 41.2), val selection: ModelSelection = ModelSelection("opus", "high"), val closing: ((AppState) -> Unit)? = null)

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
    Shot("usage-panel", prepare = { state, _, _ -> state.usageOpen = true }),
    Shot("usage-panel-high", limits = limitsAt(72.0, 93.4), prepare = { state, _, _ -> state.usageOpen = true }),
    Shot("usage-panel-none", limits = null, prepare = { state, _, _ -> state.usageOpen = true }),
    Shot("model-picker", prepare = { state, _, _ -> state.modelPickerOpen = true }),
    Shot("model-picker-default", selection = ModelSelection.DEFAULT, prepare = { state, _, _ -> state.modelPickerOpen = true }),
    Shot("model-picker-custom", selection = ModelSelection("claude-opus-4-7", "xhigh"), prepare = { state, _, _ -> state.modelPickerOpen = true }),
    Shot("model-running-free", prepare = { state, _, _ -> state.openSession("s-idle"); state.modelPickerOpen = true }),
    Shot("model-running-free-closed", prepare = { state, _, _ -> state.openSession("s-idle") }),
    Shot("model-running-working", prepare = { state, _, _ -> state.openSession("s-working") }),
    Shot("model-ended-session", prepare = { state, _, _ -> state.views[1] = state.viewOf(1).openSession("s-gone"); state.modelPickerOpen = true }),
    Shot("session-model", prepare = { state, _, _ -> state.openSession("s-working") }),
    Shot("shortcuts",prepare = { state, _, _ -> state.shortcutsOpen = true }),
    Shot("palette", prepare = { state, _, _ -> state.paletteOpen = true }),
    Shot("teams", prepare = { state, _, _ -> openTeams(state) }),
    Shot("teams-many", prepare = { state, _, _ -> addRoles(state, 9) }),
    Shot("teams-only-lead", prepare = { state, _, _ -> openTeams(state); state.teams.newTeam(); waitFor { state.teams.selected?.name == "New team" } }),
    Shot("teams-none", prepare = { state, _, _ -> openTeams(state); state.teams.teams.forEach { state.teams.requestDeleteTeam(it); state.confirm?.onConfirm?.invoke() }; state.confirm = null; waitFor { state.teams.teams.isEmpty() } }),
    Shot("role-edit", prepare = { state, _, _ -> editRole(state, 1) }),
    Shot("role-edit-lead", prepare = { state, _, _ -> editRole(state, 0) }),
    Shot("role-edit-tools", prepare = { state, _, _ -> editRole(state, 2) }),
    Shot("role-new", prepare = { state, _, _ -> newRole(state) }),
    Shot("role-invalid", prepare = { state, _, _ -> newRole(state) { it.model = "-evil"; it.tools = ToolSelection(emptyList()); it.name.edit { replace(0, length, "Bad Name") } } }),
    Shot("role-tools-custom", prepare = { state, _, _ -> editRole(state, 1) { it.tools = ToolSelection.parse("Read, Grep, Glob, Edit, mcp__github__*, mcp__db__query") } }),
    Shot("task-dialog", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.taskDialogProjectId = 1 }),
    Shot("task-dialog-no-teams", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.teams.teams.forEach { state.teams.requestDeleteTeam(it); state.confirm?.onConfirm?.invoke() }; state.confirm = null; waitFor { state.teams.teams.isEmpty() }; state.taskDialogProjectId = 1 }),
    Shot("session-team", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.sessions.sessionTeams["s-working"] = state.teams.teams.first(); state.openSession("s-working") }),
    Shot("agents-roles", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.sessions.sessionTeams["s-working"] = state.teams.teams.first() }),
    Shot("agents-rail", prepare = { state, _, _ -> state.openSession("s-agents") }),
    Shot("agents-rail-finished", prepare = { state, _, _ -> state.openSession("s-agents"); state.agents.toggleFinished("s-agents") }),
    Shot("agents-log", prepare = { state, _, _ -> openAgentLog(state, 2) }),
    Shot("agents-log-live", prepare = { state, _, _ -> openAgentLog(state, 0) }),
    Shot("progress",prepare = { _, git, data -> data.progressDelayMillis = 4_000; git.fetchNow() }),
    Shot("plugins-installed", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED) }),
    Shot("plugins-installed-detail", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.plugins.selectedInstalled = state.plugins.installed.first { it.id.startsWith("frontend-design") }.key(); waitFor { state.plugins.details.values.any { it is DetailsState.Loaded } } }),
    Shot("plugins-marketplace", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE) }),
    Shot("plugins-marketplace-detail", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE); state.plugins.selectedAvailable = "code-review@claude-plugins-official"; waitFor { state.plugins.details.values.any { it is DetailsState.Loaded } } }),
    Shot("plugins-sources", prepare = { state, _, _ -> openPlugins(state, PluginsTab.SOURCES) }),
    Shot("plugins-command", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE); state.plugins.install(NEEDS_COMMAND_PLUGIN, ExtensionScope.USER, null); waitFor { state.plugins.pending != null } }),
    Shot("plugins-failure", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.plugins.failure = ExtensionFailure("Updating legacy-linter", "Could not update legacy-linter@acme-internal", "error: marketplace acme-internal is unreachable\nfatal: unable to access 'https://git.acme.dev/platform/claude-plugins.git/': Could not resolve host") }),
    Shot("plugins-busy", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.plugins.update(state.plugins.installed.first { it.id.startsWith("context7") }); waitFor { state.plugins.job != null } }),
    Shot("mcp-list", prepare = { state, _, _ -> openMcp(state); waitFor { state.mcp.healthCheck == HealthCheck.DONE } }),
    Shot("mcp-checking", prepare = { state, _, _ -> openMcp(state) }),
    Shot("mcp-detail", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(ExtensionScope.USER, "github"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-needed", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(ExtensionScope.USER, "notion"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-logged-in", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(ExtensionScope.USER, "github"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-waiting", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(ExtensionScope.USER, "sentry"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } }; state.mcp.signIn("sentry") }),
    Shot("mcp-auth-connector", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(null, "claude.ai Canva"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-stdio", prepare = { state, _, _ -> openMcp(state); state.mcp.selected = mcpKey(ExtensionScope.USER, "context7"); waitFor { state.mcp.healthCheck == HealthCheck.DONE && state.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-add-dialog", prepare = { state, _, _ -> openMcp(state); state.mcp.adding = true }),
    Shot("skills-list", prepare = { state, _, _ -> openSkills(state) }),
    Shot("skills-detail", prepare = { state, _, _ -> openSkills(state); state.skills.selected = state.skills.skills.first { it.name == "code-review" }.file.toString(); waitFor { state.skills.texts.values.any { it is SkillText.Loaded } } }),
    Shot("project-plugins", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.PLUGINS) }),
    Shot("project-plugins-marketplace", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.PLUGINS).plugins.tab = PluginsTab.MARKETPLACE }),
    Shot("project-mcp", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.MCP); waitFor { state.projectSettings?.mcp?.healthCheck == HealthCheck.DONE } }),
    Shot("project-mcp-add", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.MCP).mcp.adding = true }),
    Shot("project-skills", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.SKILLS) }),
    Shot("project-skills-new", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.SKILLS).skills.creating = true }),
    Shot("skills-new-dialog", prepare = { state, _, _ -> openSkills(state); state.skills.creating = true }),
    Shot("proposal-questions", prepare = { state, _, _ -> state.openSession("s-agents") }),
    Shot("proposal-permission", prepare = { state, _, _ -> state.openSession("s-needs") }),
    Shot("proposal-plan", prepare = { state, _, _ -> state.openSession("s-infra") }),
    Shot("proposal-form", prepare = { state, _, _ -> state.openSession("s-working") }),
    Shot("proposal-questions-multi", prepare = { state, _, _ -> state.openSession("s-agents"); questions(state).apply { toggle(0, 1); goTo(1); toggle(1, 0); toggle(1, 2) } }),
    Shot("proposal-questions-other", prepare = { state, _, _ -> state.openSession("s-agents"); questions(state).apply { goTo(2); toggleOther(2); other[2].edit { replace(0, length, "In the wiki") } } }),
    Shot("proposal-hidden", prepare = { state, _, _ -> state.openSession("s-needs"); waitFor { state.proposalDialog != null }; state.proposals.hide(state.proposalDialog!!) }),
    Shot("proposal-overview", prepare = { state, _, _ -> state.navigate(Route.Overview) }),
    Shot("dialog-confirm", prepare = { state, _, _ -> state.confirm = ConfirmRequest("Remove project", "Remove ccm-sandbox from the list? Files on disk are not touched.", "Remove", true) {} }, closing = { it.confirm = null }),
    Shot("dialog-new-skill-close", prepare = { state, _, _ -> openSkills(state); state.skills.creating = true }, closing = { it.skills.creating = false }),
)

private fun GitState.pushNow() { push(); waitFor { error != null } }

private fun GitState.fetchNow() { fetch(); waitFor { job != null } }


/** Runs coroutine continuations only when the render thread pumps them, so Compose state is never written during a frame. */
private class PumpedDispatcher : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }

    fun pump() {
        while (true) queue.poll()?.run() ?: return
    }
}

private val shotLoop = PumpedDispatcher()

private fun openAgentLog(state: AppState, index: Int) {
    state.openSession("s-agents")
    state.agents.openLog("s-agents", state.live.find("s-agents")!!.subagents[index])
    waitFor { state.agents.log?.entries != null }
}

private fun waitFor(timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    shotLoop.pump()
    while (!condition() && System.currentTimeMillis() < deadline) {
        Thread.sleep(POLL_MILLIS)
        shotLoop.pump()
    }
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
    val world = PreviewWorld(git = data, rateLimits = shot.limits, selection = shot.selection)
    val scope = CoroutineScope(SupervisorJob() + shotLoop)
    val state = AppState(world.backend(PreviewTerminal), scope, UiSettingsStore(null)).also {
        it.updateSettings { s -> s.copy(assistantStartup = AssistantStartup.NEVER) }
        it.start()
        it.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
    waitFor { state.projects.all.isNotEmpty() && state.installation != null }
    state.navigate(Route.Project(1))
    val git = state.openGit(1, shot.view)!!
    git.attach()
    waitFor { git.status != null && (git.status?.repository() == false || (git.branches.isNotEmpty() && git.stashes.isNotEmpty())) }
    shot.prepare(state, git, data)
    val scene = ImageComposeScene(width, height, Density(1f)) { AppTheme(dark) { Column { TopBar(state); AppRoot(state, osName = "Windows 11", modifier = Modifier.weight(1f)) } } }
    try {
        var frame = 0L
        repeat(SETTLE_FRAMES) { shotLoop.pump(); scene.render(frame++ * FRAME_NANOS) }
        waitFor(SETTLE_MILLIS) { false }
        repeat(SETTLE_FRAMES) { shotLoop.pump(); scene.render(frame++ * FRAME_NANOS) }
        fun write(suffix: String) {
            val bytes = scene.render(frame * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes
            val file = out.resolve("${shot.name}$suffix-${if (dark) "dark" else "light"}-${width}x$height.png")
            Files.write(file, bytes)
            println("wrote $file")
        }
        write("")
        shot.closing?.let { close ->
            close(state)
            val closedAt = frame
            CLOSE_FRAMES.forEach { target ->
                while (frame < closedAt + target) { shotLoop.pump(); scene.render(frame++ * FRAME_NANOS) }
                write("-close$target")
            }
        }
    } finally {
        scope.cancel()
        // Compose RectManager intermittently throws when a popup or banner scene is disposed before its last layout; the PNGs are already written.
        runCatching { scene.close() }
    }
}

private fun openTeams(state: AppState): Team {
    state.navigate(Route.Teams)
    waitFor { state.teams.selected != null }
    return state.teams.selected!!
}

private fun editRole(state: AppState, index: Int, adjust: (RoleForm) -> Unit = {}) {
    val team = openTeams(state)
    state.teams.editRole(team, team.roles[index])
    adjust(state.teams.dialog!!.form)
}

private fun newRole(state: AppState, adjust: (RoleForm) -> Unit = {}) {
    state.teams.newRole(openTeams(state))
    adjust(state.teams.dialog!!.form)
}

private fun addRoles(state: AppState, count: Int) {
    repeat(count) { index ->
        val team = openTeams(state)
        state.teams.newRole(team)
        val form = state.teams.dialog!!.form
        form.name.edit { replace(0, length, "agent-${index + 1}") }
        form.description.edit { replace(0, length, "Handles work package ${index + 1} when the lead splits a large task.") }
        form.prompt.edit { replace(0, length, "You handle work package ${index + 1}.") }
        form.model = listOf("sonnet", "haiku", null, "opus")[index % 4]
        form.effort = listOf("low", null, "medium", "high")[index % 4]
        form.tools = ToolSelection.parse(listOf("Read, Grep, Glob", null, "Read, Write, Edit, Bash, mcp__github__*", "Read")[index % 4])
        state.teams.saveRole(state.teams.dialog!!) { }
        waitFor { state.teams.dialog == null && state.teams.selected!!.roles.size == team.roles.size + 1 }
    }
}

private fun openPlugins(state: AppState, tab: PluginsTab) {
    state.navigate(Route.Plugins)
    state.plugins.tab = tab
    state.plugins.ensureLoaded()
    state.plugins.loadCatalog()
    waitFor { state.plugins.loaded && state.plugins.catalog.isNotEmpty() }
}

private fun openProjectSettings(state: AppState, section: ProjectSettingsSection): ProjectSettingsState {
    state.openProjectSettings(state.projects.all.first { it.id == 1L }, section)
    val settings = state.projectSettings!!
    settings.plugins.ensureLoaded()
    settings.plugins.loadCatalog()
    settings.mcp.ensureLoaded()
    settings.skills.ensureLoaded()
    waitFor { settings.plugins.loaded && settings.mcp.loaded && settings.skills.loaded }
    return settings
}

private fun openMcp(state: AppState) {
    state.navigate(Route.Mcp)
    state.mcp.ensureLoaded()
    waitFor { state.mcp.loaded }
}

private fun openSkills(state: AppState) {
    state.navigate(Route.Skills)
    state.skills.ensureLoaded()
    waitFor { state.skills.loaded }
}

private fun questions(state: AppState): QuestionsDraft {
    waitFor { state.proposalDialog != null }
    val proposal = state.proposalDialog as QuestionsProposal
    return state.proposals.draft(proposal) { QuestionsDraft(proposal.questions()) }
}
