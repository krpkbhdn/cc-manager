package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.author.AuthorProfileService
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventStore
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgentService
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.config.AppProperties
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import com.github.krpkbhdn.ccmanager.notification.NotificationService
import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService
import com.github.krpkbhdn.ccmanager.project.ProjectService
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService
import com.github.krpkbhdn.ccmanager.run.RunService
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.github.krpkbhdn.ccmanager.shell.ShellDetector
import com.github.krpkbhdn.ccmanager.shell.ShellLauncher
import com.github.krpkbhdn.ccmanager.team.TeamService
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalView
import com.github.krpkbhdn.ccmanager.usage.RateLimitService
import com.github.krpkbhdn.ccmanager.update.UpdateService
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.DirectoryOpener
import com.github.krpkbhdn.ccmanager.workspace.IdeLauncher
import java.nio.file.Path
import org.springframework.context.ConfigurableApplicationContext

/** The terminal widget layer, behind an interface so the UI can be previewed without a real PTY. */
interface TerminalPort {
    fun open(session: LaunchedSession)
    fun openShell(shell: LaunchedShell) {}
    fun send(ccmSessionId: String, text: String)
    fun close(ccmSessionId: String)

    /** Moves the keyboard focus into the session's terminal, if it has one. */
    fun focus(ccmSessionId: String) {}

    /** True when a Ctrl+C reached the session less than [windowMillis] ago. */
    fun ctrlCWithin(ccmSessionId: String, windowMillis: Long): Boolean = false

    /** True when the user pressed Enter less than [windowMillis] ago, before the hook marks the session WORKING. */
    fun enterWithin(ccmSessionId: String, windowMillis: Long): Boolean = false

    /** True while the user may have an unsent draft: typed or pasted since the last Enter, Ctrl+C or Esc. */
    fun draftMayExist(ccmSessionId: String): Boolean = false

    @Composable
    fun View(ccmSessionId: String, modifier: Modifier)
}

object HostTerminalPort : TerminalPort {
    override fun open(session: LaunchedSession) = TerminalHost.open(session)
    override fun openShell(shell: LaunchedShell) = TerminalHost.open(shell)
    override fun send(ccmSessionId: String, text: String) = TerminalHost.send(ccmSessionId, text)
    override fun close(ccmSessionId: String) = TerminalHost.close(ccmSessionId)
    override fun focus(ccmSessionId: String) = TerminalHost.focus(ccmSessionId)
    override fun ctrlCWithin(ccmSessionId: String, windowMillis: Long) = TerminalHost.ctrlCWithin(ccmSessionId, windowMillis)
    override fun enterWithin(ccmSessionId: String, windowMillis: Long) = TerminalHost.enterWithin(ccmSessionId, windowMillis)
    override fun draftMayExist(ccmSessionId: String) = TerminalHost.draftMayExist(ccmSessionId)

    @Composable
    override fun View(ccmSessionId: String, modifier: Modifier) = TerminalView(ccmSessionId, modifier, dark = colors.dark, background = colors.terminal)
}

/** Every backend collaborator the UI talks to. */
class Backend(
    val projects: ProjectService,
    val installation: ClaudeInstallationService,
    val sessions: SessionRegistry,
    val notifications: NotificationService,
    val launcher: ClaudeLauncher,
    val backgroundAgents: BackgroundAgentService,
    val hooks: HookEventStore,
    val directories: DirectoryOpener,
    val ide: IdeLauncher,
    val git: GitBackend,
    val rateLimits: RateLimitService,
    val sessionUsage: SessionUsageService,
    val teams: TeamService,
    val plugins: PluginService,
    val mcp: McpService,
    val skills: SkillService,
    val transcripts: TranscriptService,
    val runConfigurations: RunConfigurationService,
    val runs: RunService,
    val shellDetector: ShellDetector,
    val shellLauncher: ShellLauncher,
    val author: AuthorProfileService,
    val proposals: ProposalService,
    val promptTemplates: PromptTemplateService,
    val terminal: TerminalPort = HostTerminalPort,
    val updates: UpdateService? = null,
    val sound: NotificationSoundService? = null,
    val dataDir: Path = Path.of(System.getProperty("user.home"), ".cc-manager"),
    val sessionProperties: SessionProperties,
) {
    companion object {
        fun from(context: ConfigurableApplicationContext) = Backend(
            projects = context.getBean(ProjectService::class.java),
            installation = context.getBean(ClaudeInstallationService::class.java),
            sessions = context.getBean(SessionRegistry::class.java),
            notifications = context.getBean(NotificationService::class.java),
            launcher = context.getBean(ClaudeLauncher::class.java),
            backgroundAgents = context.getBean(BackgroundAgentService::class.java),
            hooks = context.getBean(HookEventStore::class.java),
            directories = context.getBean(DirectoryOpener::class.java),
            ide = context.getBean(IdeLauncher::class.java),
            git = GitBackend.from(context),
            rateLimits = context.getBean(RateLimitService::class.java),
            sessionUsage = context.getBean(SessionUsageService::class.java),
            teams = context.getBean(TeamService::class.java),
            plugins = context.getBean(PluginService::class.java),
            mcp = context.getBean(McpService::class.java),
            skills = context.getBean(SkillService::class.java),
            transcripts = context.getBean(TranscriptService::class.java),
            runConfigurations = context.getBean(RunConfigurationService::class.java),
            runs = context.getBean(RunService::class.java),
            shellDetector = context.getBean(ShellDetector::class.java),
            shellLauncher = context.getBean(ShellLauncher::class.java),
            author = context.getBean(AuthorProfileService::class.java),
            proposals = context.getBean(ProposalService::class.java),
            promptTemplates = context.getBean(PromptTemplateService::class.java),
            updates = context.getBean(UpdateService::class.java),
            sound = context.getBean(NotificationSoundService::class.java),
            dataDir = context.getBean(AppProperties::class.java).dataDir(),
            sessionProperties = context.getBean(SessionProperties::class.java),
        )
    }
}
