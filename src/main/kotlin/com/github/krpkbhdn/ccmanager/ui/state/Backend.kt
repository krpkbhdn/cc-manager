package com.github.krpkbhdn.ccmanager.ui.state

import java.time.Duration
import com.github.krpkbhdn.ccmanager.app.AppControlProperties
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry
import com.github.krpkbhdn.ccmanager.author.AuthorProfileService
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventStore
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgentService
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpTool
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry
import com.github.krpkbhdn.ccmanager.config.AppProperties
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import com.github.krpkbhdn.ccmanager.notification.NotificationService
import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService
import com.github.krpkbhdn.ccmanager.project.ProjectService
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService
import com.github.krpkbhdn.ccmanager.run.RunService
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService
import com.github.krpkbhdn.ccmanager.shell.ShellDetector
import com.github.krpkbhdn.ccmanager.shell.ShellLauncher
import com.github.krpkbhdn.ccmanager.team.TeamService
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.usage.RateLimitService
import com.github.krpkbhdn.ccmanager.update.UpdateService
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceBackend
import com.github.krpkbhdn.ccmanager.voice.VoiceInputService
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService
import com.github.krpkbhdn.ccmanager.workspace.DirectoryOpener
import com.github.krpkbhdn.ccmanager.workspace.IdeLauncher
import java.nio.file.Path
import org.springframework.context.ConfigurableApplicationContext

/** Every backend collaborator the UI talks to. */
class Backend(
    val projects: ProjectService,
    val groups: ProjectGroupService,
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
    val sessionSearch: SessionSearchService,
    val terminal: TerminalPort = HostTerminalPort,
    val updates: UpdateService? = null,
    val sound: NotificationSoundService? = null,
    val dataDir: Path = Path.of(System.getProperty("user.home"), ".cc-manager"),
    val sessionProperties: SessionProperties,
    val appControl: AppControlRegistry? = null,
    val appControlProperties: AppControlProperties = AppControlProperties(Duration.ofSeconds(10), 20_000),
    val assistantTools: List<AssistantMcpTool> = emptyList(),
    val voice: VoiceBackend? = null,
) {
    companion object {
        fun from(context: ConfigurableApplicationContext) = Backend(
            projects = context.getBean(ProjectService::class.java),
            groups = context.getBean(ProjectGroupService::class.java),
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
            sessionSearch = context.getBean(SessionSearchService::class.java),
            updates = context.getBean(UpdateService::class.java),
            sound = context.getBean(NotificationSoundService::class.java),
            dataDir = context.getBean(AppProperties::class.java).dataDir(),
            sessionProperties = context.getBean(SessionProperties::class.java),
            appControl = context.getBean(AppControlRegistry::class.java),
            appControlProperties = context.getBean(AppControlProperties::class.java),
            assistantTools = context.getBeansOfType(AssistantMcpTool::class.java).values.toList(),
            voice = VoiceBackend(
                context.getBean(VoiceInputService::class.java),
                context.getBean(DeviceService::class.java),
                context.getBean(VoiceAssetService::class.java),
                context.getBean(Microphone::class.java),
            ),
        )
    }
}
