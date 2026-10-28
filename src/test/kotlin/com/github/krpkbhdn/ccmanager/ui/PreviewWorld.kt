package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventStore
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgentService
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.usage.RateLimitService
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentState
import java.nio.file.Files
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import tools.jackson.databind.json.JsonMapper
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.notification.NotificationService
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.project.ProjectService
import com.github.krpkbhdn.ccmanager.run.ActiveRun
import com.github.krpkbhdn.ccmanager.run.RunConfiguration
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService
import com.github.krpkbhdn.ccmanager.run.RunService
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import com.github.krpkbhdn.ccmanager.ui.state.Backend
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import com.github.krpkbhdn.ccmanager.shell.ShellDetector
import com.github.krpkbhdn.ccmanager.shell.ShellLauncher
import com.github.krpkbhdn.ccmanager.shell.ShellProfile
import com.github.krpkbhdn.ccmanager.workspace.DirectoryOpener
import com.github.krpkbhdn.ccmanager.workspace.IdeLauncher
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.OptionalInt
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.Text
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import com.github.krpkbhdn.ccmanager.author.AuthorProfileService
import org.mockito.Mockito.`when`

private fun ago(seconds: Long): Instant = Instant.now().minusSeconds(seconds)

private val previewTranscriptDir: java.nio.file.Path by lazy { Files.createTempDirectory("ccm-projects") }

/** A real folder so the run configurations of the first preview project do not show as missing. */
private val previewProjectDir: Path by lazy {
    Files.createTempDirectory("ccm-preview").resolve("cc-manager").also { Files.createDirectories(it.resolve(".claude/worktrees/feature-x")) }
}

private val previewTranscript: String by lazy {
    Files.createTempFile(previewTranscriptDir, "ccm-agent", ".jsonl").also {
        Files.writeString(it, listOf(
            """{"type":"user","message":{"role":"user","content":"Run the StatusReducer tests and report failures."}}""",
            """{"type":"assistant","message":{"content":[{"type":"text","text":"Running the reducer tests first."},{"type":"tool_use","name":"Bash","input":{"command":"./gradlew test --tests '*StatusReducerTest'"}}]}}""",
            """{"type":"user","message":{"content":[{"type":"tool_result","content":"BUILD SUCCESSFUL in 14s\n9 tests completed, 0 failed"}]}}""",
            """{"type":"assistant","message":{"content":[{"type":"text","text":"All 9 tests pass."}]}}""",
        ).joinToString("\n"))
    }.toString()
}

private fun previewAgent(
    id: String, type: String, state: SubagentState, startedAgo: Long, activity: String = "", ended: Long? = null,
    calls: Int = 0, message: String = "", background: Boolean = false, stale: Boolean = false,
) = SubagentSnapshot(id, type, activity, state, background, calls, state == SubagentState.FAILED, message, previewTranscript, ago(startedAgo), ago(ended ?: if (stale) STALE_SECONDS else 4), ended?.let { ago(it) }, stale)

private const val STALE_SECONDS = 1_320L

private const val FIVE_HOUR_RESET_SECONDS = 8_040L
private const val SEVEN_DAY_RESET_SECONDS = 3 * 86_400L + 4 * 3_600L

/** Usage limits as Claude would report them right now, for previews. */
fun limitsAt(fiveHourPercent: Double, sevenDayPercent: Double) = RateLimitSnapshot(
    RateLimitWindow(fiveHourPercent, Instant.now().plusSeconds(FIVE_HOUR_RESET_SECONDS)),
    RateLimitWindow(sevenDayPercent, Instant.now().plusSeconds(SEVEN_DAY_RESET_SECONDS)),
    Instant.now(),
)

/** In-memory backend data for the dev preview; Mockito keeps it independent of the real services' constructors. */
class PreviewWorld(many: Boolean = false, empty: Boolean = false, val git: FakeRepoData = SampleRepos.busy(), val rateLimits: RateLimitSnapshot? = limitsAt(23.5, 41.2), selection: ModelSelection = ModelSelection("opus", "high")) {
    init { git.notRepositories.add(Path.of("C:/Users/dev/Work/docs-site")) }
    val fakeMcp = FakeMcp()
    val proposals = PreviewProposals()
    val sessionUsage = mapOf(
        "s-working" to SessionUsage("s-working", "Opus 4.7", "xhigh", 62.0, 4.38, 2_400_000L, Instant.now()),
        "s-needs" to SessionUsage("s-needs", "Sonnet 5.5", "medium", 18.5, 0.42, 1_200_000L, Instant.now()),
        "s-idle" to SessionUsage("s-idle", "Opus 4.7", null, 91.0, 12.07, 5_000_000L, Instant.now()),
    )
    @Volatile var claudeInstalled = true
    private val changeListeners = mutableListOf<Runnable>()
    private val notificationListeners = mutableListOf<Consumer<AppNotification>>()

    private val extraNames = listOf(
        "customer-portal-frontend-with-a-really-long-name", "ml-pipeline", "mobile-ios", "mobile-android",
        "data-warehouse", "auth-service", "marketing-site", "design-system",
    )

    val projects: List<Project> = if (empty) emptyList() else listOf(
        Project(1, "cc-manager", previewProjectDir, ago(9000), selection),
        Project(2, "billing-api", Path.of("C:/Users/dev/Work/billing-api"), ago(8000)),
        Project(3, "docs-site", Path.of("C:/Users/dev/Work/docs-site"), ago(7000)),
        Project(4, "infra-terraform", Path.of("C:/Users/dev/Work/infra-terraform"), ago(6000)),
    ) + if (many) extraNames.mapIndexed { index, name -> Project(5L + index, name, Path.of("C:/Users/dev/Work/clients/$name"), ago(5000L - index)) } else emptyList()

    private val extraSessions = if (!many || empty) emptyList() else extraNames.indices.filter { it % 2 == 0 }.map { index ->
        val status = listOf(AgentStatus.WORKING, AgentStatus.IDLE, AgentStatus.NEEDS_ACTION, AgentStatus.IDLE)[index / 2 % 4]
        SessionSnapshot(
            "s-extra-$index", 5L + index, "cx$index", status, "Running Bash: npm run build", "default", "claude-opus-4-7",
            "Session $index", ago(900), ago(5L + index * 7), emptyList(), 0, null,
        )
    }

    private val crowdedSessions = if (!many || empty) emptyList() else (1..8).map { n ->
        SessionSnapshot("s-crowd-$n", 1, "cc$n", if (n % 3 == 0) AgentStatus.NEEDS_ACTION else AgentStatus.IDLE, "Finished", "default", "claude-opus-4-7", "Crowded tab number $n with a long topic", ago(600), ago(30L * n), emptyList(), 0, null)
    }

    val sessions: List<SessionSnapshot> = (if (empty) emptyList() else baseSessions()) + extraSessions + crowdedSessions

    private fun baseSessions() = listOf(
        SessionSnapshot(
            "s-agents", 1, "c5", AgentStatus.NEEDS_ACTION, "Delegating to developer and tester", "acceptEdits", "claude-opus-4-7",
            "Build the agents rail", ago(1800), ago(3),
            listOf(
                previewAgent("g1", "developer", SubagentState.WORKING, 754, "Bash: ./gradlew test --tests '*StatusReducerTest'", calls = 41, background = true),
                previewAgent("g6", "Explore", SubagentState.WORKING, 2400, "Grep: SubagentStop", calls = 18, background = true, stale = true),
                previewAgent("g2", "tester", SubagentState.NEEDS_ACTION, 95, "Waiting for permission: Bash", calls = 6),
                previewAgent("g3", "Explore", SubagentState.DONE, 900, ended = 840, calls = 12, message = "The rail lives in AgentsPanel.kt; subagents come from SessionRegistry."),
                previewAgent("g4", "code-reviewer", SubagentState.FAILED, 600, ended = 520, calls = 9, message = "API Error: overloaded, gave up after 3 retries."),
                previewAgent("g5", "developer", SubagentState.INTERRUPTED, 500, ended = 470, calls = 3, message = ""),
            ),
            0, null,
        ),
        SessionSnapshot(
            "s-working", 1, "c1", AgentStatus.WORKING, "Running Bash: ./gradlew test", "acceptEdits", "claude-opus-4-7",
            "Refactor the session registry to read titles from the transcript", ago(2400), ago(4),
            listOf(
                previewAgent("a1", "code-reviewer", SubagentState.WORKING, 60, "Read: SessionRegistry.java", calls = 7),
                previewAgent("a2", "Explore", SubagentState.DONE, 300, ended = 240, message = "Found 12 usages of AgentStatus"),
            ),
            1, null,
        ),
        SessionSnapshot(
            "s-needs", 1, "c2", AgentStatus.NEEDS_ACTION, "Permission needed: Edit NotificationServiceTest.java", "default",
            "claude-sonnet-5-5", "Fix flaky notification test", ago(1200), ago(95), emptyList(), 0, null,
        ),
        SessionSnapshot(
            "s-idle", 2, "c3", AgentStatus.IDLE, "Finished: invoice export endpoint added", "plan", "claude-opus-4-7",
            "Add invoice export", ago(5000), ago(310), emptyList(), 0, null,
        ),
        SessionSnapshot(
            "s-infra", 4, "c4", AgentStatus.NEEDS_ACTION, "Plan approval: migrate state backend to S3", "plan", "claude-opus-4-7",
            null, ago(800), ago(20), emptyList(), 0, null,
        ),
        SessionSnapshot(
            "s-silent", 2, "c6", AgentStatus.WORKING, "Running Bash: npm run docs:build && npm run docs:lint && npm run docs:link-check && npm run docs:deploy -- --environment=staging --verbose --retries=3", "default", "claude-opus-4-7",
            "Rebuild the docs site", ago(2400), ago(1500), emptyList(), 0, null,
        ),
    )

    val notifications = listOf(
        AppNotification(1, ago(3600), NotificationKind.FINISHED, 2, "s-idle", "Finished", "Invoice export endpoint added and tests pass."),
        AppNotification(2, ago(900), NotificationKind.INFO, 3, null, "Claude signed in", "Session restored for docs-site."),
        AppNotification(3, ago(120), NotificationKind.NEEDS_ACTION, 1, "s-needs", "Needs your action", "Permission needed: Edit NotificationServiceTest.java"),
        AppNotification(4, ago(30), NotificationKind.NEEDS_ACTION, 4, "s-infra", "Plan approval", "Claude proposes to migrate the state backend to S3."),
        AppNotification(5, ago(15), NotificationKind.ERROR, 1, "s-working", "API error", "Rate limited, retrying in 20s."),
    )

    private val activity = listOf(
        ActivityEntry(ago(300), HookEventType.USER_PROMPT_SUBMIT, null, "Refactor the session registry to use immutable snapshots"),
        ActivityEntry(ago(280), HookEventType.PRE_TOOL_USE, null, "Read src/main/java/.../SessionRegistry.java"),
        ActivityEntry(ago(260), HookEventType.POST_TOOL_USE, null, "Read finished (212 lines)"),
        ActivityEntry(ago(200), HookEventType.SUBAGENT_START, "a1", "code-reviewer started"),
        ActivityEntry(ago(190), HookEventType.PRE_TOOL_USE, "a1", "Grep AgentStatus"),
        ActivityEntry(ago(120), HookEventType.PERMISSION_REQUEST, null, "Edit SessionRegistry.java"),
        ActivityEntry(ago(100), HookEventType.POST_TOOL_USE, null, "Edit applied"),
        ActivityEntry(ago(60), HookEventType.SUBAGENT_STOP, "a1", "code-reviewer finished"),
        ActivityEntry(ago(30), HookEventType.PRE_TOOL_USE, null, "Bash ./gradlew test"),
        ActivityEntry(ago(10), HookEventType.STOP_FAILURE, null, "Rate limited (retryable)"),
        ActivityEntry(ago(4), HookEventType.STOP, null, "Turn finished"),
    )

    private val runConfigurations = if (empty) emptyList() else listOf(
        RunConfiguration(1, 1, "dev server", "npm run dev", previewProjectDir),
        RunConfiguration(2, 1, "feature-x tests", "./gradlew test --continuous", previewProjectDir.resolve(".claude/worktrees/feature-x")),
        RunConfiguration(3, 1, "old experiment", "make serve", previewProjectDir.resolve(".claude/worktrees/gone")),
    )
    private val activeRuns = ConcurrentHashMap<Long, ActiveRun>().apply {
        if (!empty) {
            put(1, ActiveRun(1, "run-1-1", "dev server", null, OptionalInt.empty()))
            put(2, ActiveRun(2, "run-2-1", "feature-x tests", null, OptionalInt.of(1)))
        }
    }
    private val runListeners = mutableListOf<Runnable>()

    fun fire() = changeListeners.toList().forEach { it.run() }

    private fun runConfigurationService(): RunConfigurationService {
        val service = mock(RunConfigurationService::class.java)
        `when`(service.list(anyLong())).thenAnswer { runConfigurations.filter { c -> c.projectId() == it.getArgument<Long>(0) } }
        `when`(service.subscribe(any())).thenAnswer { Runnable { } }
        return service
    }

    private fun runService(): RunService {
        val service = mock(RunService::class.java)
        `when`(service.current(anyLong())).thenAnswer { Optional.ofNullable(activeRuns[it.getArgument<Long>(0)]) }
        `when`(service.subscribe(any())).thenAnswer { runListeners += it.getArgument<Runnable>(0); Runnable { } }
        `when`(service.start(anyLong())).thenAnswer {
            val config = runConfigurations.first { c -> c.id() == it.getArgument<Long>(0) }
            val runId = "run-${config.id()}-${System.nanoTime()}"
            activeRuns[config.id()] = ActiveRun(config.id(), runId, config.name(), null, OptionalInt.empty())
            runListeners.toList().forEach { listener -> listener.run() }
            LaunchedSession(runId, config.projectId(), config.name(), null)
        }
        return service
    }

    fun backend(terminal: TerminalPort): Backend {
        val projectService = mock(ProjectService::class.java)
        `when`(projectService.list()).thenReturn(projects)
        `when`(projectService.find(anyLong())).thenAnswer { Optional.ofNullable(projects.firstOrNull { p -> p.id == it.getArgument<Long>(0) }) }
        `when`(projectService.subscribe(any())).thenAnswer { Runnable { } }
        `when`(projectService.selectModel(anyLong(), any())).thenAnswer { projects.first { p -> p.id == it.getArgument<Long>(0) } }

        val registry = mock(SessionRegistry::class.java)
        `when`(registry.sessions()).thenAnswer { sessions }
        `when`(registry.sessions(anyLong())).thenAnswer { sessions.filter { s -> s.projectId == it.getArgument<Long>(0) } }
        `when`(registry.projectStatus(anyLong())).thenAnswer {
            sessions.filter { s -> s.projectId == it.getArgument<Long>(0) }.maxOfOrNull { s -> s.status } ?: AgentStatus.OFFLINE
        }
        `when`(registry.subscribe(any())).thenAnswer { changeListeners += it.getArgument<Runnable>(0); Runnable { } }

        val notificationService = mock(NotificationService::class.java)
        `when`(notificationService.history()).thenReturn(notifications)
        `when`(notificationService.subscribe(any())).thenAnswer { notificationListeners += it.getArgument<Consumer<AppNotification>>(0); Runnable { } }

        val installation = mock(ClaudeInstallationService::class.java)
        `when`(installation.current()).thenAnswer { installationNow() }
        `when`(installation.recheck()).thenAnswer { installationNow() }

        val launcher = mock(ClaudeLauncher::class.java)
        `when`(launcher.launch(anyLong(), any())).thenThrow(IllegalStateException("Preview mode: Claude cannot be launched here."))
        `when`(launcher.launchTeam(anyLong(), any(), anyString())).thenThrow(IllegalStateException("Preview mode: Claude cannot be launched here."))
        `when`(launcher.attach(anyLong(), anyString())).thenThrow(IllegalStateException("Preview mode: nothing to attach to."))

        val background = mock(BackgroundAgentService::class.java)
        `when`(background.list(any())).thenReturn(listOf(BackgroundAgent("bg-1", "nightly-lint", "running", "Linting 340 files")))

        val hooks = mock(HookEventStore::class.java)
        `when`(hooks.recent(anyString(), anyInt())).thenReturn(activity)

        val ide = mock(IdeLauncher::class.java)
        `when`(ide.available()).thenReturn(true)

        val limits = mock(RateLimitService::class.java)
        `when`(limits.current()).thenAnswer { Optional.ofNullable(rateLimits) }
        `when`(limits.subscribe(any())).thenAnswer { Runnable { } }

        val usage = mock(SessionUsageService::class.java)
        `when`(usage.all()).thenAnswer { sessionUsage }
        `when`(usage.subscribe(any())).thenAnswer { Runnable { } }

        val shellDetector = mock(ShellDetector::class.java)
        `when`(shellDetector.detect()).thenReturn(listOf(ShellProfile("PowerShell", Path.of("pwsh.exe"), emptyList()), ShellProfile("Git Bash", Path.of("bash.exe"), listOf("--login", "-i"))))
        val shellLauncher = mock(ShellLauncher::class.java)
        `when`(shellLauncher.launch(anyLong(), any())).thenThrow(IllegalStateException("Preview mode: shells cannot be started here."))

        return Backend(projectService, installation, registry, notificationService, launcher, background, hooks, mock(DirectoryOpener::class.java), ide, FakeGit(git).backend, limits, usage, previewTeamService(), FakePlugins(), fakeMcp.service, FakeSkills(), TranscriptService(JsonMapper.builder().build(), previewTranscriptDir), runConfigurationService(), runService(), shellDetector, shellLauncher, mock(AuthorProfileService::class.java), proposals.service(), mock(PromptTemplateService::class.java), terminal = terminal, sessionProperties = SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(10)))
    }

    private fun installationNow() = if (claudeInstalled) {
        ClaudeInstallation(true, Path.of("claude"), "2.1.143 (Claude Code)", LoginState.LOGGED_IN, null)
    } else {
        ClaudeInstallation(false, null, null, LoginState.UNKNOWN, "The claude command was not found in PATH or in ~/.local/bin.")
    }

    fun emit(notification: AppNotification) = notificationListeners.toList().forEach { it.accept(notification) }
}

/** Stand-in for the real terminal widget so layouts can be judged without a PTY. */
object PreviewTerminal : TerminalPort {
    override fun open(session: LaunchedSession) = Unit
    override fun send(ccmSessionId: String, text: String) = Unit
    override fun close(ccmSessionId: String) = Unit

    @Composable
    override fun View(ccmSessionId: String, modifier: Modifier) {
        Column(modifier.fillMaxSize().background(colors.terminal).padding(16.dp)) {
            listOf("> claude", "", "  Claude Code v2.1.143", "  Opus 4.7 - ~/Work/cc-manager", "", "> Refactor the session registry to use immutable snapshots", "", "  Reading SessionRegistry.java ...", "  Edit src/main/java/.../SessionRegistry.java")
                .forEach { Text(it, color = colors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
        }
    }
}
