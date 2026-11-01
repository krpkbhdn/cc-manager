package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Creates the isolated worktree and branch a task runs in and remembers it in SQLite. Blocking; git failures throw {@link GitException}. */
@Service
public class TaskWorktreeService {

    private static final String SELECT = "SELECT id, project_id, path, branch, base_branch, task FROM task_worktree";

    private final GitBranchService branches;
    private final GitWorktreeService worktrees;
    private final JdbcClient jdbc;

    @Autowired
    public TaskWorktreeService(GitBranchService branches, GitWorktreeService worktrees, JdbcClient jdbc) {
        this.branches = branches;
        this.worktrees = worktrees;
        this.jdbc = jdbc;
    }

    /**
     * Adds branch {@code task/<slug>} from the current HEAD in a sibling folder of {@code repo} and records it for the project.
     * A name is skipped when a local or remote branch or the folder already exists; the worktree is removed again if recording fails.
     */
    public TaskWorktree create(long projectId, Path repo, String task) {
        var all = branches.branches(repo);
        var names = all.stream().map(TaskWorktreeService::withoutRemote).collect(Collectors.toSet());
        var base = all.stream().filter(b -> b.current() && !b.remote()).map(GitBranch::name).findFirst().orElse("");
        String branch;
        Path target;
        try {
            branch = TaskBranchNames.branchFor(task, name -> names.contains(name) || Files.exists(folderOf(repo, name)));
            target = folderOf(repo, branch);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot create a task worktree for this project folder: " + e.getMessage(), e);
        }
        worktrees.add(repo, branch, "", target);
        try {
            jdbc.sql("INSERT INTO task_worktree (project_id, path, branch, base_branch, task) VALUES (?, ?, ?, ?, ?)")
                    .param(projectId).param(target.toString()).param(branch).param(base).param(task).update();
        } catch (RuntimeException e) {
            rollBack(repo, target, branch, e);
            throw e;
        }
        return find(target).orElseThrow();
    }

    /** Best effort: the folder and the branch this call created; a failure here never hides the original one. */
    private void rollBack(Path repo, Path target, String branch, RuntimeException original) {
        try {
            worktrees.remove(repo, target, true);
        } catch (RuntimeException e) {
            original.addSuppressed(e);
        }
        try {
            branches.delete(repo, branch, true);
        } catch (RuntimeException e) {
            original.addSuppressed(e);
        }
    }

    /** The project's task worktrees, oldest first; rows whose folder has vanished are dropped first. */
    public List<TaskWorktree> forProject(long projectId) {
        pruneMissing(projectId);
        return jdbc.sql(SELECT + " WHERE project_id = ? ORDER BY id").param(projectId).query(TaskWorktreeService::mapRow).list();
    }

    public Optional<TaskWorktree> find(Path path) {
        return jdbc.sql(SELECT).query(TaskWorktreeService::mapRow).list().stream()
                .filter(row -> FolderIdentity.same(row.path(), path)).findFirst();
    }

    /** Matches the folder by identity, so a path spelled differently by git still finds its row. */
    public void forget(Path path) {
        jdbc.sql("SELECT path FROM task_worktree").query(String.class).list().stream()
                .filter(stored -> FolderIdentity.same(Path.of(stored), path))
                .forEach(stored -> jdbc.sql("DELETE FROM task_worktree WHERE path = ?").param(stored).update());
    }

    /** Drops rows git no longer lists; with an unreachable project folder (unplugged drive) or a failing git nothing is dropped. */
    public void pruneMissing(long projectId) {
        var repo = jdbc.sql("SELECT path FROM project WHERE id = ?").param(projectId).query(String.class).optional().map(Path::of);
        if (repo.isEmpty() || !Files.isDirectory(repo.get())) {
            return;
        }
        List<Worktree> listed;
        try {
            listed = worktrees.list(repo.get());
        } catch (RuntimeException e) {
            return;
        }
        jdbc.sql("SELECT path FROM task_worktree WHERE project_id = ?").param(projectId).query(String.class).list().stream()
                .map(Path::of).filter(path -> listed.stream().noneMatch(w -> FolderIdentity.same(w.path(), path) && !w.prunable()))
                .filter(path -> !Files.exists(path)).forEach(this::forget);
    }

    private static TaskWorktree mapRow(ResultSet rs, int row) throws SQLException {
        return new TaskWorktree(rs.getLong("id"), rs.getLong("project_id"), Path.of(rs.getString("path")),
                rs.getString("branch"), rs.getString("base_branch"), rs.getString("task"));
    }

    private static Path folderOf(Path repo, String branch) {
        return Path.of(WorktreeNames.defaultPath(repo, branch));
    }

    /** "origin/task/x" collides with a new local task/x on the first push, so the remote prefix is dropped. */
    private static String withoutRemote(GitBranch branch) {
        return branch.remote() ? branch.name().substring(branch.name().indexOf('/') + 1) : branch.name();
    }
}
