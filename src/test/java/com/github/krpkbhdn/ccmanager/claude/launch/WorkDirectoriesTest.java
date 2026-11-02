package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.git.Worktree;
import com.github.krpkbhdn.ccmanager.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkDirectoriesTest {

    @TempDir
    Path root;

    private GitWorktreeService worktrees;
    private WorkDirectories directories;
    private Path repo;
    private Project project;

    @BeforeEach
    void prepare() throws Exception {
        worktrees = mock(GitWorktreeService.class);
        directories = new WorkDirectories(worktrees);
        repo = Files.createDirectories(root.resolve("repo"));
        project = new Project(1, "app", repo, Instant.now());
    }

    private static Worktree entry(Path path, boolean prunable) {
        return new Worktree(path, "task/x", "abc", false, false, false, prunable, false, false);
    }

    @Test
    void groupIsRefused() {
        var group = new Project(2, "g", repo, Instant.now(), project.modelSelection(), true, null);

        assertThatThrownBy(() -> directories.validate(group, repo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectFolderIsAccepted() {
        assertThat(directories.accepts(project, repo)).isTrue();
    }

    @Test
    void listedWorktreeIsAccepted() throws Exception {
        var linked = Files.createDirectories(root.resolve("repo-wt-x"));
        when(worktrees.list(repo)).thenReturn(List.of(entry(linked, false)));

        assertThat(directories.accepts(project, linked)).isTrue();
    }

    @Test
    void foreignFolderIsRefused() throws Exception {
        var foreign = Files.createDirectories(root.resolve("other"));
        when(worktrees.list(repo)).thenReturn(List.of());

        assertThat(directories.accepts(project, foreign)).isFalse();
    }

    @Test
    void gitFailureIsNotTurnedIntoRefusal() throws Exception {
        var linked = Files.createDirectories(root.resolve("repo-wt-y"));
        when(worktrees.list(repo)).thenThrow(new IllegalStateException("git failed"));

        assertThatThrownBy(() -> directories.accepts(project, linked)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void symlinkedProjectPathIsAccepted() throws Exception {
        var link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, repo);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            Assumptions.abort("symbolic links are not allowed here");
        }

        assertThat(directories.accepts(new Project(3, "l", link, Instant.now()), repo)).isTrue();
    }

    @Test
    void prunableWorktreeWithMissingFolderIsRefused() {
        var gone = root.resolve("repo-wt-gone");
        when(worktrees.list(repo)).thenReturn(List.of(entry(gone, true)));

        assertThat(directories.accepts(project, gone)).isFalse();
    }
}
