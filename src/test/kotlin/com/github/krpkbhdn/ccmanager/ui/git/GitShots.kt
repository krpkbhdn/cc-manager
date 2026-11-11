package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.chrome.TopBar
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.limitsAt
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.ui.PreviewAuthorStatuses
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.PreviewPresets
import com.github.krpkbhdn.ccmanager.ui.PreviewApply
import com.github.krpkbhdn.ccmanager.ui.samplePlan
import com.github.krpkbhdn.ccmanager.ui.seedHeavy
import com.github.krpkbhdn.ccmanager.ui.seedMany
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService
import com.github.krpkbhdn.ccmanager.ui.presets.ComponentEditor
import com.github.krpkbhdn.ccmanager.ui.presets.PresetTab
import com.github.krpkbhdn.ccmanager.ui.presets.PresetsState
import com.github.krpkbhdn.ccmanager.ui.presets.apply.ApplyPresetState
import com.github.krpkbhdn.ccmanager.ui.presets.apply.ApplyStep
import com.github.krpkbhdn.ccmanager.ui.author.CatalogTab
import com.github.krpkbhdn.ccmanager.ui.settings.SettingsCategory
import com.github.krpkbhdn.ccmanager.ui.author.CatalogView
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
    Shot("task-context", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT) }),
    Shot("task-context-popup", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT).contextPopupOpen = true }),
    Shot("task-context-no-task", prepare = { state, git, data -> inTaskWorktree(state, git, data, null) }),
    Shot("task-context-merged", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT).apply { mergedInto = "main" } }),
    Shot("task-context-main-popup", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT); state.gits.switchTo(1, null); state.gits.of(1)!!.contextPopupOpen = true }),
    Shot("task-worktrees", GitSubView.WORKTREES, prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT).show(GitSubView.WORKTREES) }),
    Shot("task-context-session-tab", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT); state.sessions.sessionDirectories["s-idle"] = git.repo.resolveSibling("ccm-sandbox-wt-fix-login"); state.openSession("s-idle") }),
    Shot("task-context-sessions", prepare = { state, git, data -> inTaskWorktree(state, git, data, TASK_TEXT); state.navigate(Route.Sessions) }),
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
    Shot("context-statusbar-critical", prepare = { state, _, _ -> state.openSession("s-idle") }),
    Shot("context-statusbar-high", prepare = { state, _, _ -> state.openSession("s-working") }),
    Shot("context-details", prepare = { state, _, _ -> state.openSession("s-idle"); state.contextSessionId = "s-idle"; state.contextShowDetails = true; state.contextBreakdown.load("s-idle", true); waitFor { state.contextBreakdown.loadOf("s-idle") is com.github.krpkbhdn.ccmanager.ui.state.BreakdownLoad.Done } }),
    Shot("context-nodata", prepare = { state, _, _ -> state.openSession("s-idle"); state.contextSessionId = "s-idle"; state.contextShowDetails = true }),
    Shot("context-dialog", prepare = { state, _, _ -> state.openSession("s-idle"); state.contextSessionId = "s-idle"; state.contextBreakdown.load("s-idle", true); waitFor { state.contextBreakdown.loadOf("s-idle") is com.github.krpkbhdn.ccmanager.ui.state.BreakdownLoad.Done } }),
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
    Shot("task-dialog", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.tasks.projectId = 1 }),
    Shot("task-dialog-no-teams", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.teams.teams.forEach { state.teams.requestDeleteTeam(it); state.confirm?.onConfirm?.invoke() }; state.confirm = null; waitFor { state.teams.teams.isEmpty() }; state.tasks.projectId = 1 }),
    Shot("session-team", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.sessions.sessionTeams["s-working"] = state.teams.teams.first(); state.openSession("s-working") }),
    Shot("agents-roles", prepare = { state, _, _ -> waitFor { state.teams.teams.isNotEmpty() }; state.sessions.sessionTeams["s-working"] = state.teams.teams.first() }),
    Shot("agents-rail", prepare = { state, _, _ -> state.openSession("s-agents") }),
    Shot("agents-rail-finished", prepare = { state, _, _ -> state.openSession("s-agents"); state.agents.toggleFinished("s-agents") }),
    Shot("agents-log", prepare = { state, _, _ -> openAgentLog(state, 2) }),
    Shot("agents-log-live", prepare = { state, _, _ -> openAgentLog(state, 0) }),
    Shot("progress",prepare = { _, git, data -> data.progressDelayMillis = 4_000; git.fetchNow() }),
    Shot("plugins-installed", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED) }),
    Shot("plugins-installed-detail", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.extensions.plugins.selectedInstalled = state.extensions.plugins.installed.first { it.id.startsWith("frontend-design") }.key(); waitFor { state.extensions.plugins.details.values.any { it is DetailsState.Loaded } } }),
    Shot("plugins-marketplace", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE) }),
    Shot("plugins-marketplace-detail", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE); state.extensions.plugins.selectedAvailable = "code-review@claude-plugins-official"; waitFor { state.extensions.plugins.details.values.any { it is DetailsState.Loaded } } }),
    Shot("plugins-sources", prepare = { state, _, _ -> openPlugins(state, PluginsTab.SOURCES) }),
    Shot("plugins-command", prepare = { state, _, _ -> openPlugins(state, PluginsTab.MARKETPLACE); state.extensions.plugins.install(NEEDS_COMMAND_PLUGIN, ExtensionScope.USER, null); waitFor { state.extensions.plugins.pending != null } }),
    Shot("plugins-failure", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.extensions.plugins.failure = ExtensionFailure("Updating legacy-linter", "Could not update legacy-linter@acme-internal", "error: marketplace acme-internal is unreachable\nfatal: unable to access 'https://git.acme.dev/platform/claude-plugins.git/': Could not resolve host") }),
    Shot("plugins-busy", prepare = { state, _, _ -> openPlugins(state, PluginsTab.INSTALLED); state.extensions.plugins.update(state.extensions.plugins.installed.first { it.id.startsWith("context7") }); waitFor { state.extensions.plugins.job != null } }),
    Shot("mcp-list", prepare = { state, _, _ -> openMcp(state); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE } }),
    Shot("mcp-checking", prepare = { state, _, _ -> openMcp(state) }),
    Shot("mcp-detail", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(ExtensionScope.USER, "github"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-needed", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(ExtensionScope.USER, "notion"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-logged-in", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(ExtensionScope.USER, "github"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-waiting", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(ExtensionScope.USER, "sentry"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } }; state.extensions.mcp.signIn("sentry") }),
    Shot("mcp-auth-connector", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(null, "claude.ai Canva"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-auth-stdio", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.selected = mcpKey(ExtensionScope.USER, "context7"); waitFor { state.extensions.mcp.healthCheck == HealthCheck.DONE && state.extensions.mcp.details.values.any { it is RawDetails.Loaded } } }),
    Shot("mcp-add-dialog", prepare = { state, _, _ -> openMcp(state); state.extensions.mcp.adding = true }),
    Shot("skills-list", prepare = { state, _, _ -> openSkills(state) }),
    Shot("skills-detail", prepare = { state, _, _ -> openSkills(state); state.extensions.skills.selected = state.extensions.skills.skills.first { it.name == "code-review" }.file.toString(); waitFor { state.extensions.skills.texts.values.any { it is SkillText.Loaded } } }),
    Shot("project-plugins", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.PLUGINS) }),
    Shot("project-plugins-marketplace", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.PLUGINS).plugins.tab = PluginsTab.MARKETPLACE }),
    Shot("project-mcp", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.MCP); waitFor { state.extensions.projectSettings?.mcp?.healthCheck == HealthCheck.DONE } }),
    Shot("project-mcp-add", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.MCP).mcp.adding = true }),
    Shot("project-skills", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.SKILLS) }),
    Shot("project-skills-new", prepare = { state, _, _ -> openProjectSettings(state, ProjectSettingsSection.SKILLS).skills.creating = true }),
    Shot("skills-new-dialog", prepare = { state, _, _ -> openSkills(state); state.extensions.skills.creating = true }),
    Shot("proposal-questions", prepare = { state, _, _ -> state.openSession("s-agents") }),
    Shot("proposal-permission", prepare = { state, _, _ -> state.openSession("s-needs") }),
    Shot("proposal-plan", prepare = { state, _, _ -> state.openSession("s-infra") }),
    Shot("proposal-form", prepare = { state, _, _ -> state.openSession("s-working") }),
    Shot("proposal-questions-multi", prepare = { state, _, _ -> state.openSession("s-agents"); questions(state).apply { toggle(0, 1); goTo(1); toggle(1, 0); toggle(1, 2) } }),
    Shot("proposal-questions-other", prepare = { state, _, _ -> state.openSession("s-agents"); questions(state).apply { goTo(2); toggleOther(2); other[2].edit { replace(0, length, "In the wiki") } } }),
    Shot("proposal-hidden", prepare = { state, _, _ -> state.openSession("s-needs"); waitFor { state.answers.dialog != null }; state.proposals.hide(state.answers.dialog!!) }),
    Shot("proposal-overview", prepare = { state, _, _ -> state.navigate(Route.Overview) }),
    Shot("author-tools", prepare = { state, _, _ -> openAuthor(state) }),
    Shot("author-config", prepare = { state, _, _ -> openAuthor(state, CatalogTab.CONFIG) }),
    Shot("author-installed", prepare = { state, _, _ -> openAuthor(state, mixed = false) }),
    Shot("author-empty", prepare = { state, _, _ -> openAuthor(state, mixed = false).onlyNew = true }),
    Shot("author-crowded", prepare = { state, _, _ -> PreviewAuthorStatuses.crowded = true; openAuthor(state) }),
    Shot("settings-appearance", prepare = { state, _, _ -> state.settingsDialog.show(SettingsCategory.APPEARANCE) }, closing = { it.settingsDialog.close() }),
    Shot("settings-notifications", prepare = { state, _, _ -> state.settingsDialog.show(SettingsCategory.NOTIFICATIONS) }, closing = { it.settingsDialog.close() }),
    Shot("settings-voice", prepare = { state, _, _ -> state.settingsDialog.show(SettingsCategory.VOICE) }, closing = { it.settingsDialog.close() }),
    Shot("settings-about", prepare = { state, _, _ -> state.settingsDialog.show(SettingsCategory.ABOUT) }, closing = { it.settingsDialog.close() }),
    Shot("author-search", prepare = { state, _, _ -> openAuthor(state).query.edit { replace(0, length, "ponytail") } }),
    Shot("author-search-crowded", prepare = { state, _, _ -> PreviewAuthorStatuses.crowded = true; openAuthor(state).query.edit { replace(0, length, "review") } }),
    Shot("author-start-offer", prepare = { state, _, _ -> PreviewAuthorStatuses.mixed = true; state.author.offerAtStart(); waitFor { state.author.catalog != null } }),
    Shot("author-start-offer-crowded", prepare = { state, _, _ -> PreviewAuthorStatuses.mixed = true; PreviewAuthorStatuses.crowded = true; state.author.offerAtStart(); waitFor { state.author.catalog != null } }),
    Shot("dialog-confirm", prepare = { state, _, _ -> state.confirm = ConfirmRequest("Remove project", "Remove ccm-sandbox from the list? Files on disk are not touched.", "Remove", true) {} }, closing = { it.confirm = null }),
    Shot("dialog-new-skill-close", prepare = { state, _, _ -> openSkills(state); state.extensions.skills.creating = true }, closing = { it.extensions.skills.creating = false }),
    Shot("presets-general", prepare = { state, _, _ -> openPresets(state) }),
    Shot("presets-general-skills", prepare = { state, _, _ -> openPresets(state, PresetTab.SKILLS) }),
    Shot("presets-general-files", prepare = { state, _, _ -> openPresets(state, PresetTab.FILES) }),
    Shot("presets-general-plugins-empty", prepare = { state, _, _ -> openPresets(state, PresetTab.PLUGINS) }),
    Shot("presets-general-settings-empty", prepare = { state, _, _ -> openPresets(state, PresetTab.SETTINGS) }),
    Shot("presets-many", prepare = { state, _, _ -> openPresets(state, seed = { it.seedMany(MANY_PRESETS); null }, expected = MANY_PRESETS + 1) }),
    Shot("presets-many-search", prepare = { state, _, _ -> openPresets(state, seed = { it.seedMany(MANY_PRESETS); null }, expected = MANY_PRESETS + 1).query.edit { replace(0, length, "spring") } }),
    Shot("presets-heavy", prepare = { state, _, _ -> openHeavy(state, PresetTab.INSTRUCTIONS) }),
    Shot("presets-heavy-skills", prepare = { state, _, _ -> openHeavy(state, PresetTab.SKILLS) }),
    Shot("presets-heavy-plugins", prepare = { state, _, _ -> openHeavy(state, PresetTab.PLUGINS) }),
    Shot("presets-heavy-mcp", prepare = { state, _, _ -> openHeavy(state, PresetTab.MCP) }),
    Shot("presets-heavy-agents", prepare = { state, _, _ -> openHeavy(state, PresetTab.AGENTS) }),
    Shot("presets-heavy-files", prepare = { state, _, _ -> openHeavy(state, PresetTab.FILES) }),
    Shot("presets-heavy-settings", prepare = { state, _, _ -> openHeavy(state, PresetTab.SETTINGS) }),
    Shot("presets-dialog-plugins", prepare = { state, _, _ -> openHeavy(state, PresetTab.PLUGINS).let { it.open(ComponentEditor.PLUGINS, it.selected!!.id()) }; waitFor { state.presets.pluginChoices != null } }),
    Shot("presets-dialog-skill", prepare = { state, _, _ -> openHeavy(state, PresetTab.SKILLS).let { it.open(ComponentEditor.SKILL_ADD, it.selected!!.id()) }; waitFor { state.presets.skillChoices != null } }),
    Shot("presets-dialog-skill-edit", prepare = { state, _, _ -> openComponent(state, PresetTab.SKILLS, ComponentEditor.SKILL_EDIT) }),
    Shot("presets-dialog-instruction", prepare = { state, _, _ -> openComponent(state, PresetTab.INSTRUCTIONS, ComponentEditor.INSTRUCTION) }),
    Shot("presets-dialog-instruction-new", prepare = { state, _, _ -> openHeavy(state, PresetTab.INSTRUCTIONS).let { it.open(ComponentEditor.INSTRUCTION, it.selected!!.id()) } }),
    Shot("presets-dialog-mcp", prepare = { state, _, _ -> openHeavy(state, PresetTab.MCP).let { it.open(ComponentEditor.MCP, it.selected!!.id()) } }),
    Shot("presets-dialog-mcp-edit", prepare = { state, _, _ -> openComponent(state, PresetTab.MCP, ComponentEditor.MCP) }),
    Shot("presets-dialog-agent", prepare = { state, _, _ -> openComponent(state, PresetTab.AGENTS, ComponentEditor.AGENT) }),
    Shot("presets-dialog-file", prepare = { state, _, _ -> openComponent(state, PresetTab.FILES, ComponentEditor.FILE) }),
    Shot("presets-dialog-settings", prepare = { state, _, _ -> openComponent(state, PresetTab.SETTINGS, ComponentEditor.SETTINGS) }),
    Shot("apply-choose", prepare = { state, _, _ -> openApplyChoose(state) }),
    Shot("apply-review", prepare = { state, _, _ -> openApplyReview(state).toggleExpanded(9L) }),
    Shot("apply-review-conflict", prepare = { state, _, _ -> PreviewApply.plan = samplePlan().filter { it.componentId() in setOf(2L, 7L, 9L, 10L) }; openApplyReview(state).toggleExpanded(9L) }),
    Shot("apply-review-long", prepare = { state, _, _ -> openApplyReview(state).toggleExpanded(1L) }),
    Shot("apply-review-loading", prepare = { state, _, _ -> PreviewApply.planDelayMillis = LOADING_MILLIS; state.applyPreset.open(1, generalId(state)) }),
    Shot("apply-result", prepare = { state, _, _ -> openApplyReview(state).apply(); waitFor { state.applyPreset.step == ApplyStep.RESULT } }),
    Shot("apply-offer", prepare = { state, _, _ -> generalId(state); PreviewApply.applied = emptyMap(); state.navigate(Route.Project(2)); waitFor { state.presetOffer.offer != null } }),
)

private const val MANY_PRESETS = 25
private const val LOADING_MILLIS = 20_000L

private fun generalId(state: AppState): Long {
    waitFor { state.presets.presets.isNotEmpty() }
    return state.presets.presets.first().id()
}

private fun openApplyChoose(state: AppState) {
    PreviewPresets.current.seedMany(5)
    waitFor { state.presets.presets.size >= 6 }
    PreviewApply.applied = mapOf(state.presets.presets[0].id() to state.presets.presets[0].revision(), state.presets.presets[2].id() to 0)
    state.applyPreset.open(1)
    waitFor { state.applyPreset.applied.isNotEmpty() }
}

private fun openApplyReview(state: AppState): ApplyPresetState {
    state.applyPreset.open(1, generalId(state))
    waitFor { state.applyPreset.plan != null }
    return state.applyPreset
}

private fun openPresets(state: AppState, tab: PresetTab = PresetTab.INSTRUCTIONS, seed: (ProjectPresetService) -> Long? = { null }, expected: Int = 1): PresetsState {
    val chosen = seed(PreviewPresets.current)
    state.settingsDialog.show(SettingsCategory.PRESETS)
    waitFor { state.presets.presets.size >= expected && (chosen == null || state.presets.find(chosen) != null) }
    chosen?.let { state.presets.select(state.presets.find(it)!!) }
    state.presets.tab = tab
    return state.presets
}

private fun openHeavy(state: AppState, tab: PresetTab): PresetsState = openPresets(state, tab, seed = { it.seedHeavy().id() }, expected = 2)

private fun openComponent(state: AppState, tab: PresetTab, editor: ComponentEditor): PresetsState {
    val presets = openHeavy(state, tab)
    val preset = presets.selected!!
    presets.open(editor, preset.id(), tab.componentsOf(preset).first())
    return presets
}


private fun openAuthor(state: AppState, tab: CatalogTab = CatalogTab.TOOLS, mixed: Boolean = true): CatalogView {
    PreviewAuthorStatuses.mixed = mixed
    state.author.openManage()
    waitFor { state.author.catalog != null }
    return state.author.catalog!!.also { it.tab = tab }
}

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
    PreviewAuthorStatuses.mixed = false; PreviewAuthorStatuses.crowded = false; PreviewApply.reset()
    val data = shot.data()
    val world = PreviewWorld(git = data, rateLimits = shot.limits, selection = shot.selection)
    val scope = CoroutineScope(SupervisorJob() + shotLoop)
    val state = AppState(world.backend(PreviewTerminal), scope, UiSettingsStore(null)).also {
        it.updateSettings { s -> s.copy(assistantStartup = AssistantStartup.NEVER) }
        it.start()
        it.prefs.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
    waitFor { state.projects.all.isNotEmpty() && state.environment.installation != null }
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
    state.extensions.plugins.tab = tab
    state.extensions.plugins.ensureLoaded()
    state.extensions.plugins.loadCatalog()
    waitFor { state.extensions.plugins.loaded && state.extensions.plugins.catalog.isNotEmpty() }
}

private fun openProjectSettings(state: AppState, section: ProjectSettingsSection): ProjectSettingsState {
    state.extensions.openProjectSettings(state.projects.all.first { it.id == 1L }, section)
    val settings = state.extensions.projectSettings!!
    settings.plugins.ensureLoaded()
    settings.plugins.loadCatalog()
    settings.mcp.ensureLoaded()
    settings.skills.ensureLoaded()
    waitFor { settings.plugins.loaded && settings.mcp.loaded && settings.skills.loaded }
    return settings
}

private fun openMcp(state: AppState) {
    state.navigate(Route.Mcp)
    state.extensions.mcp.ensureLoaded()
    waitFor { state.extensions.mcp.loaded }
}

private fun openSkills(state: AppState) {
    state.navigate(Route.Skills)
    state.extensions.skills.ensureLoaded()
    waitFor { state.extensions.skills.loaded }
}

private fun questions(state: AppState): QuestionsDraft {
    waitFor { state.answers.dialog != null }
    val proposal = state.answers.dialog as QuestionsProposal
    return state.proposals.draft(proposal) { QuestionsDraft(proposal.questions()) }
}

private const val TASK_TEXT = "Fix the login redirect loop after the session expires\nSee ticket 482: users land on /login again and again"

/** Adds a linked worktree (a task one when [task] is given) with a working session, switches the project to it and returns its state. */
private fun inTaskWorktree(state: AppState, main: GitState, data: FakeRepoData, task: String?): GitState {
    val linked = main.repo.resolveSibling("ccm-sandbox-wt-fix-login")
    val branch = "task/fix-login-redirect"
    data.branch = branch
    data.worktrees += listOf(
        Worktree(main.repo, "main", "a1b2c3d", true, false, false, false, false, false),
        Worktree(linked, branch, "d4e5f6a", false, false, false, false, true, false),
        Worktree(main.repo.resolveSibling("ccm-sandbox-wt-docs"), "docs/readme", "0f1e2d3", false, false, false, false, false, false),
    )
    state.sessions.sessionDirectories["s-working"] = linked
    state.gits.switchTo(1, linked)
    val shown = state.gits.of(1)!!

    shown.attach()
    waitFor(SETTLE_MILLIS) { false }
    task?.let { shown.task = TaskWorktree(1, 1, linked, branch, "main", it) }
    waitFor { shown.status != null && shown.branches.isNotEmpty() && main.worktrees.isNotEmpty() }
    waitFor(SETTLE_MILLIS) { false }
    return shown
}
