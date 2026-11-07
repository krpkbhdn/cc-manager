package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TaskWorktreeServiceTest {

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private TaskWorktreeService service;
    private JdbcClient jdbc;
    private long projectId;

    @BeforeEach
    void prepare() {
        sandbox = GitCliSandbox.create(root);
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + root.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(sandbox.repo.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        service = new TaskWorktreeService(new GitBranchService(sandbox.runner), new GitWorktreeService(sandbox.runner), jdbc);
        sandbox.write("a.txt", "base\n");
        sandbox.commitAll("base");
    }

    @Test
    void createsTaskBranchInSiblingWorktree() {
        var worktree = service.create(projectId, sandbox.repo, "Fix the login bug!");

        assertThat(worktree.path()).isEqualTo(Path.of(WorktreeNames.defaultPath(sandbox.repo, "task/fix-the-login-bug")));
        assertThat(worktree.branch()).isEqualTo("task/fix-the-login-bug");
        assertThat(Files.exists(worktree.path().resolve("a.txt"))).isTrue();
    }

    @Test
    void secondTaskWithSameTextGetsNumberedBranch() {
        service.create(projectId, sandbox.repo, "same");
        var second = service.create(projectId, sandbox.repo, "same");

        assertThat(second.path().getFileName().toString()).endsWith("task-same-2");
        assertThat(second.branch()).isEqualTo("task/same-2");
    }

    @Test
    void leftoverFolderOfTheFirstNameMakesTheNextNumberedBranchWin() throws Exception {
        Files.createDirectories(Path.of(WorktreeNames.defaultPath(sandbox.repo, "task/same")));

        var worktree = service.create(projectId, sandbox.repo, "same");

        assertThat(worktree.branch()).isEqualTo("task/same-2");
    }

    @Test
    void remoteBranchOfTheSameNameIsSkipped() {
        var origin = root.resolve("origin.git");
        sandbox.runIn(root, "init", "--bare", "-b", GitCliSandbox.MAIN, origin.toString());
        sandbox.git("remote", "add", "origin", origin.toString());
        sandbox.git("push", "origin", "HEAD:refs/heads/task/same");
        sandbox.git("fetch", "origin");

        var worktree = service.create(projectId, sandbox.repo, "same");

        assertThat(worktree.branch()).isEqualTo("task/same-2");
    }

    @Test
    void recordsProjectTaskAndTheBranchHeadWasOn() {
        sandbox.git("checkout", "-b", "feature");

        var worktree = service.create(projectId, sandbox.repo, "Do it");

        assertThat(worktree.projectId()).isEqualTo(projectId);
        assertThat(worktree.baseBranch()).isEqualTo("feature");
        assertThat(worktree.task()).isEqualTo("Do it");
        assertThat(service.find(worktree.path())).contains(worktree);
    }

    @Test
    void detachedHeadRecordsEmptyBaseBranch() {
        sandbox.git("checkout", "--detach");

        assertThat(service.create(projectId, sandbox.repo, "x").baseBranch()).isEmpty();
    }

    @Test
    void forProjectDropsRowsWhoseFolderVanished() throws Exception {
        var kept = service.create(projectId, sandbox.repo, "kept");
        var gone = service.create(projectId, sandbox.repo, "gone");
        sandbox.git("worktree", "remove", "--force", gone.path().toString());

        assertThat(service.forProject(projectId)).containsExactly(kept);
        assertThat(service.find(gone.path())).isEmpty();
    }

    @Test
    void forgetDeletesOnlyTheRow() {
        var worktree = service.create(projectId, sandbox.repo, "x");

        service.forget(worktree.path());

        assertThat(service.find(worktree.path())).isEmpty();
        assertThat(Files.exists(worktree.path())).isTrue();
    }

    @Test
    void deletingTheProjectCascadesToItsWorktrees() {
        service.create(projectId, sandbox.repo, "x");

        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM task_worktree").query(Long.class).single()).isZero();
    }

    @Test
    void failedInsertRemovesTheCreatedWorktree() {
        jdbc.sql("DROP TABLE task_worktree").update();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(projectId, sandbox.repo, "x")).isInstanceOf(RuntimeException.class);

        assertThat(Files.exists(Path.of(WorktreeNames.defaultPath(sandbox.repo, "task/x")))).isFalse();
        assertThat(sandbox.git("branch", "--list", "task/x")).isBlank();
    }

    @Test
    void failedRollbackKeepsTheOriginalExceptionAndAddsItSuppressed() throws Exception {
        jdbc.sql("DROP TABLE task_worktree").update();
        var failing = new GitWorktreeService(sandbox.runner) {
            @Override
            public void remove(Path repo, Path worktree, boolean force) {
                throw new GitException(GitException.Kind.OTHER, "cannot remove", "");
            }
        };
        var broken = new TaskWorktreeService(new GitBranchService(sandbox.runner), failing, jdbc);

        var thrown = org.assertj.core.api.Assertions.catchThrowable(() -> broken.create(projectId, sandbox.repo, "x"));

        assertThat(thrown).isNotInstanceOf(GitException.class);
        assertThat(thrown.getSuppressed()).anyMatch(e -> e.getMessage().contains("cannot remove"));
    }

    @Test
    void rowIsKeptWhenTheProjectFolderIsUnreachable() throws Exception {
        var worktree = service.create(projectId, sandbox.repo, "kept");
        sandbox.git("worktree", "remove", "--force", worktree.path().toString());
        jdbc.sql("UPDATE project SET path = ?").param(root.resolve("unplugged").toString()).update();

        assertThat(service.forProject(projectId)).containsExactly(worktree);
    }
}
