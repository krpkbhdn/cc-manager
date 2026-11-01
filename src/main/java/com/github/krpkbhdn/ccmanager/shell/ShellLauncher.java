package com.github.krpkbhdn.ccmanager.shell;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.claude.launch.PtySpawner;
import com.github.krpkbhdn.ccmanager.claude.launch.WorkDirectories;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Starts a plain interactive shell in a PTY inside the project directory; not a Claude session, so no hooks and no registry entry. */
@Service
public class ShellLauncher {

    static final String TERMINAL_ID_PREFIX = "shell-";
    private static final Logger log = LoggerFactory.getLogger(ShellLauncher.class);

    private final ProjectService projects;

    private final WorkDirectories workDirectories;

    public ShellLauncher(ProjectService projects, WorkDirectories workDirectories) {
        this.projects = projects;
        this.workDirectories = workDirectories;
    }

    /** @throws IllegalStateException with a user-readable message (project gone, spawn failed) */
    public LaunchedShell launch(long projectId, ShellProfile shell) {
        return spawn(projectId, shell, project(projectId).path());
    }

    /** A shell in [directory], which must be the project folder or one of its git worktrees; the shell still belongs to the project. */
    public LaunchedShell launch(long projectId, ShellProfile shell, Path directory) {
        return spawn(projectId, shell, workDirectories.validate(project(projectId), directory));
    }

    private Project project(long projectId) {
        Project project = projects.find(projectId)
                .orElseThrow(() -> new IllegalStateException("The project no longer exists."));
        if (!Files.isDirectory(project.path())) {
            throw new IllegalStateException("The project folder is missing: " + project.path());
        }
        return project;
    }

    private LaunchedShell spawn(long projectId, ShellProfile shell, Path directory) {
        try {
            var process = PtySpawner.spawn(shell.command(), directory, ClaudeCommand.terminalEnvironment(System.getenv()));
            return new LaunchedShell(TERMINAL_ID_PREFIX + UUID.randomUUID(), projectId, shell, process);
        } catch (IOException | RuntimeException e) {
            log.warn("{} failed to start in {}", shell.name(), directory, e);
            throw new IllegalStateException("Could not start " + shell.name() + ": " + e.getMessage(), e);
        }
    }
}
