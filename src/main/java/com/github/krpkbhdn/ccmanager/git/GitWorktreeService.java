package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.WorktreePorcelainParser.ParsedWorktree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Lists, adds and removes git worktrees. Blocking; failures throw {@link GitException}, bad input IllegalArgumentException. */
@Service
public class GitWorktreeService {

    private static final String BRANCH_REF = "refs/heads/";

    private final GitRunner git;

    @Autowired
    public GitWorktreeService(GitRunner git) {
        this.git = git;
    }

    /** The main worktree first, then the linked ones; dirty and merged are computed per entry. */
    public List<Worktree> list(Path repo) {
        var parsed = WorktreePorcelainParser.parse(git.out(repo, "worktree", "list", "--porcelain"));
        var mainHead = parsed.isEmpty() ? "" : parsed.getFirst().head();
        var result = new ArrayList<Worktree>();
        for (int i = 0; i < parsed.size(); i++) {
            result.add(toWorktree(parsed.get(i), i == 0, mainHead));
        }
        return result;
    }

    /** Creates {@code branch} from {@code base} (blank = HEAD) in a new worktree at {@code target}. */
    public void add(Path repo, String branch, String base, Path target) {
        WorktreeNames.branchProblem(branch).ifPresent(problem -> {
            throw new IllegalArgumentException(problem);
        });
        WorktreeNames.pathProblem(target).ifPresent(problem -> {
            throw new IllegalArgumentException(problem);
        });
        var command = new ArrayList<>(List.of("worktree", "add", "-b", branch, target.toString()));
        if (base != null && !base.isBlank()) {
            GitRevisions.requireNotOption(base);
            command.add(base);
        }
        git.out(repo, command.toArray(String[]::new));
    }

    /** Removes a linked worktree, never its branch; a dirty one needs {@code force}. */
    public void remove(Path repo, Path worktree, boolean force) {
        var entries = list(repo);
        if (entries.getFirst().path().normalize().equals(worktree.normalize())) {
            throw new IllegalArgumentException("The main worktree cannot be removed");
        }
        var command = new ArrayList<>(List.of("worktree", "remove"));
        if (force) {
            command.add("--force");
        }
        command.add(worktree.toString());
        git.out(repo, command.toArray(String[]::new));
    }

    private Worktree toWorktree(ParsedWorktree entry, boolean main, String mainHead) {
        var present = !entry.prunable() && !entry.bare() && Files.isDirectory(entry.path());
        var merged = present && !main && !entry.detached() && !entry.branch().isEmpty() && !entry.head().equals(mainHead)
                && git.tryRun(entry.path(), "merge-base", "--is-ancestor", BRANCH_REF + entry.branch(), mainHead).ok();
        return new Worktree(entry.path(), entry.branch(), entry.head(), main, entry.detached(), entry.locked(), entry.prunable(),
                present && isDirty(entry.path()), merged);
    }

    private boolean isDirty(Path worktree) {
        var status = git.tryRun(worktree, "status", "--porcelain");
        return !status.ok() || !status.stdout().isBlank();
    }
}
