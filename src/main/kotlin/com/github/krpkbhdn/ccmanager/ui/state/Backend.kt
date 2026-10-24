package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventStore
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgentService
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.notification.NotificationService
import com.github.krpkbhdn.ccmanager.project.ProjectService
import com.github.krpkbhdn.ccmanager.team.TeamService
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalView
import com.github.krpkbhdn.ccmanager.usage.RateLimitService
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.DirectoryOpener
import com.github.krpkbhdn.ccmanager.workspace.IdeLauncher
import org.springframework.context.ConfigurableApplicationContext

/** The terminal widget layer, behind an interface so the UI can be previewed without a real PTY. */
interface TerminalPort {
    fun open(session: LaunchedSession)
    fun send(ccmSessionId: String, text: String)
    fun close(ccmSessionId: String)

    /** Moves the keyboard focus into the session's terminal, if it has one. */
    fun focus(ccmSessionId: String) {}

    @Composable
    fun View(ccmSessionId: String, modifier: Modifier)
}

object HostTerminalPort : TerminalPort {
    override fun open(session: LaunchedSession) = TerminalHost.open(session)
    override fun send(ccmSessionId: String, text: String) = TerminalHost.send(ccmSessionId, text)
    override fun close(ccmSessionId: String) = TerminalHost.close(ccmSessionId)
    override fun focus(ccmSessionId: String) = TerminalHost.focus(ccmSessionId)

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
    val terminal: TerminalPort = HostTerminalPort,
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
        )
    }
}
