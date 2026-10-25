package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Operation;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Finishing or abandoning a stopped merge/rebase/cherry-pick/revert (see {@link GitStatus#operation()}).
 * Editors never open: {@code core.editor=true} accepts the prepared commit message.
 */
@Service
public class GitConflictService {

    private static final String LITERAL_PATHSPECS = "--literal-pathspecs";
    private static final String[] NO_EDITOR = {"-c", "core.editor=true", "-c", "sequence.editor=true"};

    private final GitRunner git;

    @Autowired
    public GitConflictService(GitRunner git) {
        this.git = git;
    }

    /**
     * Takes one side of a conflicted file wholesale and stages it. OURS is the branch you are on, THEIRS the incoming
     * changes; during a rebase git swaps its own ours/theirs (ours = the branch being rebased onto), so they are swapped
     * back. A side that deleted the file resolves to deleting it.
     */
    public void resolve(Path repo, String path, ConflictSide side) {
        var file = literalPath(path);
        if (git.out(repo, LITERAL_PATHSPECS, "ls-files", "-u", "--", file).isBlank()) {
            throw new GitException(Kind.OTHER, "File is no longer conflicted: " + path, "");
        }
        var gitSide = (side == ConflictSide.OURS) == (operation(repo) != Operation.REBASE) ? "--ours" : "--theirs";
        var checkout = git.tryRun(repo, LITERAL_PATHSPECS, "checkout", gitSide, "--", file);
        if (checkout.ok()) {
            git.out(repo, LITERAL_PATHSPECS, "add", "--", file);
        } else if (checkout.stderr().contains("does not have")) {
            git.out(repo, LITERAL_PATHSPECS, "rm", "--quiet", "--force", "--", file);
        } else {
            git.out(repo, LITERAL_PATHSPECS, "checkout", gitSide, "--", file);
        }
    }

    /** The file was edited by hand (or in the IDE): stage it as resolved. */
    public void markResolved(Path repo, String path) {
        git.out(repo, LITERAL_PATHSPECS, "add", "--", literalPath(path));
    }

    private static String literalPath(String path) {
        return RepositoryPaths.validate(List.of(path)).getFirst();
    }

    /** merge --continue / rebase --continue / cherry-pick --continue / revert --continue, whichever is in progress. */
    public void continueOperation(Path repo) {
        switch (requireOperation(repo)) {
            case MERGE -> git.out(repo, withoutEditor("commit", "--no-edit"));
            case REBASE -> git.out(repo, withoutEditor("rebase", "--continue"));
            case CHERRY_PICK -> git.out(repo, withoutEditor("cherry-pick", "--continue"));
            case REVERT -> git.out(repo, withoutEditor("revert", "--continue"));
            case NONE -> throw noOperation();
        }
    }

    public void abortOperation(Path repo) {
        git.out(repo, switch (requireOperation(repo)) {
            case MERGE -> new String[] {"merge", "--abort"};
            case REBASE -> new String[] {"rebase", "--abort"};
            case CHERRY_PICK -> new String[] {"cherry-pick", "--abort"};
            case REVERT -> new String[] {"revert", "--abort"};
            case NONE -> throw noOperation();
        });
    }

    /** Rebase only: skip the current commit. */
    public void skipCommit(Path repo) {
        if (requireOperation(repo) != Operation.REBASE) {
            throw new GitException(Kind.OTHER, "Skipping a commit is only possible during a rebase", "");
        }
        git.out(repo, withoutEditor("rebase", "--skip"));
    }

    private Operation operation(Path repo) {
        return RepositoryOperations.detect(Path.of(git.out(repo, "rev-parse", "--absolute-git-dir").strip()));
    }

    private Operation requireOperation(Path repo) {
        var operation = operation(repo);
        if (operation == Operation.NONE) {
            throw noOperation();
        }
        return operation;
    }

    private static GitException noOperation() {
        return new GitException(Kind.OTHER, "No merge, rebase, cherry-pick or revert is in progress", "");
    }

    private static String[] withoutEditor(String... command) {
        var args = new String[NO_EDITOR.length + command.length];
        System.arraycopy(NO_EDITOR, 0, args, 0, NO_EDITOR.length);
        System.arraycopy(command, 0, args, NO_EDITOR.length, command.length);
        return args;
    }
}
