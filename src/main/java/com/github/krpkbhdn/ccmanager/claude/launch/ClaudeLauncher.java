package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.claude.hook.HookSettingsWriter;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Starts the native claude in a PTY inside the project directory with hooks injected via --settings, registers the
 * session and reports process exit to the registry. The UI attaches a terminal widget to the returned process.
 */
@Service
public class ClaudeLauncher {

    static final int INITIAL_COLUMNS = 120;
    static final int INITIAL_ROWS = 32;
    private static final int SPAWN_FAILED_EXIT_CODE = -1;
    private static final Logger log = LoggerFactory.getLogger(ClaudeLauncher.class);

    private final ProjectService projects;
    private final ClaudeInstallationService installation;
    private final HookSettingsWriter hookSettings;
    private final SessionRegistry sessions;

    public ClaudeLauncher(ProjectService projects, ClaudeInstallationService installation,
            HookSettingsWriter hookSettings, SessionRegistry sessions) {
        this.projects = projects;
        this.installation = installation;
        this.hookSettings = hookSettings;
        this.sessions = sessions;
    }

    /** @throws IllegalStateException with a user-readable message (Claude missing, project gone, spawn failed) */
    public LaunchedSession launch(long projectId, LaunchMode mode) {
        return start(projectId, ClaudeCommand.label(mode),
                (settings, exe, selection) -> ClaudeCommand.launchArguments(exe, settings, mode, selection));
    }

    /** A lead session with the team's roles as subagents; the task is its first prompt. */
    public LaunchedSession launchTeam(long projectId, Team team, String task) {
        return start(projectId, "claude (" + team.name() + ")",
                (settings, exe, selection) -> ClaudeCommand.teamArguments(exe, settings, team, task));
    }

    /** claude attach ID for a background agent. */
    public LaunchedSession attach(long projectId, String backgroundSessionId) {
        return start(projectId, "attach " + backgroundSessionId,
                (settings, exe, selection) -> ClaudeCommand.attachArguments(exe, settings, backgroundSessionId));
    }

    private LaunchedSession start(long projectId, String label, CommandFactory commandFactory) {
        Project project = project(projectId);
        Path directory = project.path();
        Path executable = executable();
        String ccmSessionId = sessions.register(projectId);
        try {
            Path settings = hookSettings.write(ccmSessionId);
            List<String> command = commandFactory.build(settings, executable, project.modelSelection());
            PtyProcess process = spawn(command, directory, ccmSessionId);
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
        if (!Files.isDirectory(project.path())) {
            throw new IllegalStateException("The project folder is missing: " + project.path());
        }
        return project;
    }

    private Path executable() {
        ClaudeInstallation current = installation.current();
        if (!current.installed() || current.executable() == null) {
            String reason = current.problem() == null ? "Claude Code is not installed." : current.problem();
            throw new IllegalStateException(reason);
        }
        return current.executable();
    }

    private PtyProcess spawn(List<String> command, Path directory, String ccmSessionId) throws IOException {
        return new PtyProcessBuilder(platformArguments(command))
                .setDirectory(directory.toString())
                .setUseWinConPty(true)
                .setEnvironment(ClaudeCommand.environment(System.getenv(), ccmSessionId))
                .setInitialColumns(INITIAL_COLUMNS)
                .setInitialRows(INITIAL_ROWS)
                .start();
    }

    private static String[] platformArguments(List<String> command) {
        var windows = System.getProperty("os.name").startsWith("Windows");
        if (!windows) {
            return command.toArray(String[]::new);
        }
        WindowsArgument.requireSafeForBatchFile(command);
        var quoted = command.stream().map(WindowsArgument::quote).toArray(String[]::new);
        WindowsArgument.requireFitsCommandLine(quoted);
        return quoted;
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
        List<String> build(Path settings, Path executable, ModelSelection selection);
    }
}
