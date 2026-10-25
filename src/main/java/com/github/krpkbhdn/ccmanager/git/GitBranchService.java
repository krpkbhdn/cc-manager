package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Branch listing and branch-level operations. All mutating methods throw {@link GitException}. */
@Service
public class GitBranchService {

    private static final String LOCAL_REF = "refs/heads/";
    private static final String REMOTE_REF = "refs/remotes/";
    private static final String AUTOSTASH_MESSAGE = "cc-manager autostash";
    private static final String STASH_REF = "refs/stash";

    private final GitRunner git;
    private final BranchRefs refs;

    @Autowired
    public GitBranchService(GitRunner git) {
        this.git = git;
        this.refs = new BranchRefs(git);
    }

    /** Local branches first (current first), then remote-tracking ones; with ahead/behind against upstream. */
    public List<GitBranch> branches(Path repo) {
        return BranchListParser.parse(git.out(repo, "for-each-ref", "--format=" + BranchListParser.FORMAT, "refs/heads", "refs/remotes"));
    }

    /** Checks out a local branch; a remote name like "origin/feature" creates a tracking local branch. {@code autoStash} stashes and re-applies local changes. */
    public void checkout(Path repo, String branch, boolean autoStash) {
        GitRevisions.requireNotOption(branch);
        var command = checkoutCommand(repo, branch);
        if (!autoStash) {
            git.out(repo, command);
            return;
        }
        var stashed = stashLocalChanges(repo);
        try {
            git.out(repo, command);
        } catch (GitException e) {
            if (stashed != null) {
                popStash(repo, stashed, e);
            }
            throw e;
        }
        if (stashed != null) {
            popStash(repo, stashed, null);
        }
    }

    /** Detached HEAD at any commit-ish. */
    public void checkoutCommit(Path repo, String commitish) {
        GitRevisions.requireNotOption(commitish);
        git.out(repo, "checkout", "--detach", commitish);
    }

    public void create(Path repo, String name, String startPoint, boolean checkout) {
        refs.requireValidNewName(repo, name);
        var command = new ArrayList<>(checkout ? List.of("checkout", "-b", name) : List.of("branch", name));
        if (startPoint != null) {
            GitRevisions.requireNotOption(startPoint);
            command.add(startPoint);
        }
        git.out(repo, command.toArray(String[]::new));
    }

    public void rename(Path repo, String oldName, String newName) {
        GitRevisions.requireNotOption(oldName);
        refs.requireValidNewName(repo, newName);
        git.out(repo, "branch", "-m", oldName, newName);
    }

    public void delete(Path repo, String name, boolean force) {
        GitRevisions.requireNotOption(name);
        if (refs.current(repo).filter(name::equals).isPresent()) {
            throw new GitException(Kind.OTHER, "Cannot delete the checked-out branch '" + name + "'; switch to another branch first", "");
        }
        git.out(repo, "branch", force ? "-D" : "-d", name);
    }

    /** Deletes a branch on the remote (git push remote --delete). */
    public void deleteRemote(Path repo, String remote, String name) {
        GitRevisions.requireNotOption(remote);
        GitRevisions.requireNotOption(name);
        git.run(repo, git.properties().syncTimeout(), null, null, List.of("push", remote, "--delete", name));
    }

    public void setUpstream(Path repo, String branch, String upstream) {
        GitRevisions.requireNotOption(branch);
        GitRevisions.requireNotOption(upstream);
        git.out(repo, "branch", "--set-upstream-to=" + upstream, branch);
    }

    /** Merges {@code branch} into the current branch. A conflict surfaces as GitException(CONFLICT); the repo stays in the merging state. */
    public void merge(Path repo, String branch, MergeMode mode) {
        GitRevisions.requireNotOption(branch);
        git.out(repo, "merge", mergeFlag(mode), branch);
    }

    /** Rebases the current branch onto {@code onto}. A conflict surfaces as GitException(CONFLICT); the rebase stays stopped. */
    public void rebase(Path repo, String onto) {
        GitRevisions.requireNotOption(onto);
        git.out(repo, "rebase", onto);
    }

    private static String mergeFlag(MergeMode mode) {
        return switch (mode) {
            case DEFAULT -> "--no-edit";
            case NO_FAST_FORWARD -> "--no-ff";
            case FAST_FORWARD_ONLY -> "--ff-only";
            case SQUASH -> "--squash";
        };
    }

    private String[] checkoutCommand(Path repo, String branch) {
        if (refs.exists(repo, LOCAL_REF + branch) || !refs.exists(repo, REMOTE_REF + branch)) {
            return new String[] {"checkout", branch, "--"};
        }
        var localName = branch.substring(branch.indexOf('/') + 1);
        return refs.exists(repo, LOCAL_REF + localName)
                ? new String[] {"checkout", localName, "--"}
                : new String[] {"checkout", "-b", localName, "--track", branch};
    }

    /** The hash of the autostash entry, or null when nothing was stashed. */
    private String stashLocalChanges(Path repo) {
        var before = git.tryRun(repo, "rev-parse", "-q", "--verify", STASH_REF).stdout();
        git.out(repo, "stash", "push", "-u", "-m", AUTOSTASH_MESSAGE);
        var after = git.tryRun(repo, "rev-parse", "-q", "--verify", STASH_REF);
        return after.ok() && !after.stdout().equals(before) ? after.stdout().strip() : null;
    }

    /** Re-applies the autostash; a conflict keeps the stash and is reported, after a failed checkout the original error wins. */
    private void popStash(Path repo, String stashHash, GitException checkoutFailure) {
        var top = git.tryRun(repo, "rev-parse", "-q", "--verify", STASH_REF).stdout().strip();
        var output = GitStashService.STASH_CHANGED;
        if (top.equals(stashHash)) {
            var result = git.tryRun(repo, "stash", "pop");
            if (result.ok()) {
                return;
            }
            output = (result.stderr() + "\n" + result.stdout()).strip();
        }
        if (checkoutFailure != null) {
            var message = checkoutFailure.getMessage() + "; your local changes are kept in the stash (" + AUTOSTASH_MESSAGE + ")";
            throw new GitException(checkoutFailure.kind(), message, checkoutFailure.output());
        }
        var message = "Switched branch, but re-applying your local changes failed; they are kept in the stash";
        throw new GitException(output.contains("CONFLICT") ? Kind.CONFLICT : GitFailure.classify(output), message, output);
    }
}
