package com.github.krpkbhdn.ccmanager.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.claude.launch.WorkDirectories;
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.git.Worktree;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellLauncherTest {

    @TempDir
    Path directory;

    private final ProjectService projects = mock(ProjectService.class);
    private final GitWorktreeService worktrees = mock(GitWorktreeService.class);
    private final ShellLauncher launcher = new ShellLauncher(projects, new WorkDirectories(worktrees));
    private final ShellProfile shell = new ShellProfile("fake", Path.of("no-such-shell-binary"), List.of());

    @Test
    void shellInAForeignDirectoryIsRefused() throws Exception {
        var foreign = Files.createDirectories(directory.resolveSibling(directory.getFileName() + "-foreign"));
        when(projects.find(1)).thenReturn(Optional.of(new Project(1, "p", directory, Instant.now())));
        when(worktrees.list(directory)).thenReturn(List.of());

        assertThatThrownBy(() -> launcher.launch(1, shell, foreign)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shellInAGroupIsRefused() {
        when(projects.find(2)).thenReturn(Optional.of(new Project(2, "g", directory, Instant.now(), ModelSelection.DEFAULT, true, null)));

        assertThatThrownBy(() -> launcher.launch(2, shell, directory)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shellInAWorktreeIsStartedForTheParentProject() throws Exception {
        var worktree = Files.createDirectories(directory.resolveSibling(directory.getFileName() + "-wt"));
        when(projects.find(1)).thenReturn(Optional.of(new Project(1, "p", directory, Instant.now())));
        when(worktrees.list(directory)).thenReturn(List.of(new Worktree(worktree, "task", "abc", false, false, false, false, false, false)));

        // the directory is accepted, so the failure comes from spawning the missing shell binary
        assertThatThrownBy(() -> launcher.launch(1, shell, worktree)).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Could not start fake");
    }

    @Test
    void shellGetsTerminalVariablesAndNoClaudeSession() {
        var env = ClaudeCommand.terminalEnvironment(Map.of("PATH", "/bin"));

        assertThat(env).containsEntry("PATH", "/bin").containsEntry("TERM", "xterm-256color").doesNotContainKey("CCM_SESSION");
    }

    @Test
    void shellDropsVariablesInheritedFromClaudeCode() {
        var env = ClaudeCommand.terminalEnvironment(Map.of("PATH", "/bin", "CLAUDECODE", "1", "CLAUDE_CODE_ENTRYPOINT", "cli", "NO_COLOR", "1", "FORCE_COLOR", "1"));

        assertThat(env).containsKey("PATH").doesNotContainKeys("CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "NO_COLOR", "FORCE_COLOR");
    }
}
