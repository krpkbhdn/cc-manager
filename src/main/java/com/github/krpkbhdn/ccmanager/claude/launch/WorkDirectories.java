package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.git.FolderIdentity;
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.git.Worktree;
import com.github.krpkbhdn.ccmanager.project.Project;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** Decides where a session or shell of a project may run: its own folder or one of its linked git worktrees. */
@Service
public class WorkDirectories {

    private final GitWorktreeService worktrees;

    public WorkDirectories(GitWorktreeService worktrees) {
        this.worktrees = worktrees;
    }

    /** @throws IllegalArgumentException for a group or a folder that is neither the project folder nor one of its worktrees */
    public Path validate(Project project, Path directory) {
        if (project.group()) {
            throw new IllegalArgumentException("A project group has no working folder of its own.");
        }
        Path wanted = FolderIdentity.of(directory);
        if (FolderIdentity.same(wanted, project.path()) || isWorktree(project, wanted)) {
            return wanted;
        }
        throw new IllegalArgumentException("Not a worktree of project " + project.name() + ": " + directory);
    }

    /** Like {@link #validate} but answers false instead of throwing. */
    public boolean accepts(Project project, Path directory) {
        try {
            validate(project, directory);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isWorktree(Project project, Path wanted) {
        return worktrees.list(project.path()).stream().filter(worktree -> !worktree.prunable()).map(Worktree::path)
                .anyMatch(path -> FolderIdentity.same(path, wanted));
    }
}
