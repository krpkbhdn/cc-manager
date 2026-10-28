package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.claude.hook.HookSettingsWriter;
import com.github.krpkbhdn.ccmanager.claude.hook.SessionFiles;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.plugin.ClaudePluginWriter;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.extension.mcp.ProjectMcpServers;
import com.github.krpkbhdn.ccmanager.project.GroupWorkspace;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.pty4j.PtyProcess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Starts the native claude in a PTY inside the project directory with hooks injected via --settings, registers the
 * session and reports process exit to the registry. The UI attaches a terminal widget to the returned process.
 */
@Service
public class ClaudeLauncher {

    /** Project id of sessions that belong to no project; SQLite ids start at 1. */
    public static final long ASSISTANT_PROJECT_ID = 0L;
    private static final String ASSISTANT_DIRECTORY = "assistant";
    private static final int SPAWN_FAILED_EXIT_CODE = -1;
    private static final Logger log = LoggerFactory.getLogger(ClaudeLauncher.class);

    private final ProjectService projects;
    private final ClaudeInstallationService installation;
    private final HookSettingsWriter hookSettings;
    private final ClaudePluginWriter plugin;
    private final SessionRegistry sessions;
    private final Path dataDir;
    private final ProjectGroupService groups;
    private final GroupWorkspace groupWorkspace;
    private final ProjectMcpServers projectMcpServers;

    public ClaudeLauncher(ProjectService projects, ClaudeInstallationService installation,
            HookSettingsWriter hookSettings, ClaudePluginWriter plugin, SessionRegistry sessions, AppProperties properties,
            ProjectGroupService groups, GroupWorkspace groupWorkspace, ProjectMcpServers projectMcpServers) {
        this.dataDir = properties.dataDir();
        this.groups = groups;
        this.groupWorkspace = groupWorkspace;
        this.projectMcpServers = projectMcpServers;
        this.projects = projects;
        this.installation = installation;
        this.hookSettings = hookSettings;
        this.plugin = plugin;
        this.sessions = sessions;
    }

    /** @throws IllegalStateException with a user-readable message (Claude missing, project gone, spawn failed) */
    public LaunchedSession launch(long projectId, LaunchMode mode) {
        return start(projectId, ClaudeCommand.label(mode),
                (files, exe, selection) -> ClaudeCommand.launchArguments(exe, files, plugin.directory(), mode, selection));
    }

    /** Like {@link #launch(long, LaunchMode)} but with [team] as agents, so the team also applies to a new, continued or resumed conversation. */
    public LaunchedSession launch(long projectId, LaunchMode mode, Team team) {
        return start(projectId, ClaudeCommand.label(mode, team),
                (files, exe, selection) -> ClaudeCommand.launchArguments(exe, files, plugin.directory(), mode, selection, team));
    }

    /** A lead session with the team's roles as subagents; the task is its first prompt. */
    public LaunchedSession launchTeam(long projectId, Team team, String task) {
        return start(projectId, "claude (" + team.name() + ")",
                (files, exe, selection) -> ClaudeCommand.teamArguments(exe, files, plugin.directory(), team, task));
    }

    /** A plain session without an agent team; the task is its first prompt. */
    public LaunchedSession launchTask(long projectId, String task) {
        return start(projectId, ClaudeCommand.label(LaunchMode.NEW),
                (files, exe, selection) -> ClaudeCommand.taskArguments(exe, files, plugin.directory(), selection, task));
    }

    /** A session that finishes the author's setup; the prompt points at the staged profile. */
    public LaunchedSession launchSetup(long projectId, String prompt, Path profileDirectory) {
        return start(projectId, "claude (author setup)",
                (files, exe, selection) -> ClaudeCommand.setupArguments(exe, files, plugin.directory(), prompt, profileDirectory));
    }

    /** claude attach ID for a background agent; not for a group, whose variadic --add-dir would swallow the subcommand. */
    public LaunchedSession attach(long projectId, String backgroundSessionId) {
        if (project(projectId).group()) {
            throw new IllegalStateException("Background agents cannot be attached to a project group.");
        }
        return start(projectId, "attach " + backgroundSessionId,
                (files, exe, selection) -> ClaudeCommand.attachArguments(exe, files, backgroundSessionId));
    }

    /** A general session outside any project, started in the assistant directory under {@link #ASSISTANT_PROJECT_ID}. */
    public LaunchedSession launchAssistant() {
        Path directory = dataDir.resolve(ASSISTANT_DIRECTORY);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the assistant folder: " + e.getMessage(), e);
        }
        return start(ASSISTANT_PROJECT_ID, directory, ModelSelection.DEFAULT, ClaudeCommand.label(LaunchMode.NEW),
                (files, exe, selection) -> ClaudeCommand.assistantArguments(exe, files, selection), List.of());
    }

    private LaunchedSession start(long projectId, String label, CommandFactory commandFactory) {
        Project project = project(projectId);
        return start(projectId, project.path(), project.modelSelection(), label, commandFactory,
                project.group() ? members(project) : List.of());
    }

    private LaunchedSession start(long projectId, Path directory, ModelSelection modelSelection, String label,
            CommandFactory commandFactory, List<Project> members) {
        boolean group = !members.isEmpty();
        Path executable = executable();
        String ccmSessionId = sessions.register(projectId);
        try {
            List<Path> memberPaths = members.stream().map(Project::path).toList();
            if (group) {
                groupWorkspace.prepare(directory, members);
            }
            SessionFiles files = projectId == ASSISTANT_PROJECT_ID ? hookSettings.writeAssistant(ccmSessionId)
                    : group ? hookSettings.write(ccmSessionId, projectMcpServers.approved(memberPaths))
                    : hookSettings.write(ccmSessionId);
            List<String> command = new ArrayList<>(commandFactory.build(files, executable, modelSelection));
            if (group) {
                ClaudeCommand.addDirectories(command, memberPaths);
            }
            Map<String, String> environment = group
                    ? ClaudeCommand.groupEnvironment(System.getenv(), ccmSessionId)
                    : ClaudeCommand.environment(System.getenv(), ccmSessionId);
            PtyProcess process = PtySpawner.spawn(command, directory, environment);
            watchExit(ccmSessionId, process);
            return new LaunchedSession(ccmSessionId, projectId, label, process);
        } catch (IOException | RuntimeException e) {
            cleanUp(ccmSessionId);
            log.warn("Claude failed to start in {}", directory, e);
            throw new IllegalStateException("Could not start Claude: " + e.getMessage(), e);
        }
    }

    private Project project(long projectId) {
        Project project = projects.find(projectId)
                .orElseThrow(() -> new IllegalStateException("The project no longer exists."));
        if (!project.group() && !Files.isDirectory(project.path())) {
            throw new IllegalStateException("The project folder is missing: " + project.path());
        }
        return project;
    }

    private List<Project> members(Project group) {
        List<Project> members = groups.members(group.id());
        if (members.isEmpty()) {
            throw new IllegalStateException("The group has no projects: " + group.name());
        }
        members.stream().filter(member -> !Files.isDirectory(member.path())).findFirst().ifPresent(member -> {
            throw new IllegalStateException("The project folder is missing: " + member.path() + " (" + member.name() + ")");
        });
        return members;
    }

    private Path executable() {
        ClaudeInstallation current = installation.current();
        if (!current.installed() || current.executable() == null) {
            String reason = current.problem() == null ? "Claude Code is not installed." : current.problem();
            throw new IllegalStateException(reason);
        }
        return current.executable();
    }

    private void watchExit(String ccmSessionId, PtyProcess process) {
        Thread watcher = new Thread(() -> awaitExit(ccmSessionId, process), "claude-exit-" + ccmSessionId);
        watcher.setDaemon(true);
        watcher.start();
    }

    private void awaitExit(String ccmSessionId, PtyProcess process) {
        try {
            sessions.markEnded(ccmSessionId, process.waitFor());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            deleteSettings(ccmSessionId);
        }
    }

    private void cleanUp(String ccmSessionId) {
        try {
            sessions.markEnded(ccmSessionId, SPAWN_FAILED_EXIT_CODE);
        } finally {
            deleteSettings(ccmSessionId);
        }
    }

    private void deleteSettings(String ccmSessionId) {
        try {
            hookSettings.delete(ccmSessionId);
        } catch (RuntimeException e) {
            log.debug("Could not delete hook settings of {}", ccmSessionId, e);
        }
    }

    @FunctionalInterface
    private interface CommandFactory {
        List<String> build(SessionFiles files, Path executable, ModelSelection selection);
    }
}
