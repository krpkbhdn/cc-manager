package com.github.krpkbhdn.ccmanager.shell;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.claude.launch.PtySpawner;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
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

    public ShellLauncher(ProjectService projects) {
        this.projects = projects;
    }

    /** @throws IllegalStateException with a user-readable message (project gone, spawn failed) */
    public LaunchedShell launch(long projectId, ShellProfile shell) {
        Project project = projects.find(projectId)
                .orElseThrow(() -> new IllegalStateException("The project no longer exists."));
        if (!Files.isDirectory(project.path())) {
            throw new IllegalStateException("The project folder is missing: " + project.path());
        }
        try {
            var process = PtySpawner.spawn(shell.command(), project.path(), environment(System.getenv()));
            return new LaunchedShell(TERMINAL_ID_PREFIX + UUID.randomUUID(), projectId, shell, process);
        } catch (IOException | RuntimeException e) {
            log.warn("{} failed to start in {}", shell.name(), project.path(), e);
            throw new IllegalStateException("Could not start " + shell.name() + ": " + e.getMessage(), e);
        }
    }

    static Map<String, String> environment(Map<String, String> parent) {
        return ClaudeCommand.terminalEnvironment(parent);
    }
}
