package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/** Branch listing and branch-level operations. All mutating methods throw {@link GitException}. */
@Service
public class GitBranchService {

    /** Local branches first (current first), then remote-tracking ones; with ahead/behind against upstream. */
    public List<GitBranch> branches(Path repo) { throw todo(); }

    /** Checks out a local branch; a remote name like "origin/feature" creates a tracking local branch. {@code autoStash} stashes and re-applies local changes. */
    public void checkout(Path repo, String branch, boolean autoStash) { throw todo(); }

    /** Detached HEAD at any commit-ish. */
    public void checkoutCommit(Path repo, String commitish) { throw todo(); }

    public void create(Path repo, String name, String startPoint, boolean checkout) { throw todo(); }

    public void rename(Path repo, String oldName, String newName) { throw todo(); }

    public void delete(Path repo, String name, boolean force) { throw todo(); }

    /** Deletes a branch on the remote (git push remote --delete). */
    public void deleteRemote(Path repo, String remote, String name) { throw todo(); }

    public void setUpstream(Path repo, String branch, String upstream) { throw todo(); }

    /** Merges {@code branch} into the current branch. A conflict surfaces as GitException(CONFLICT); the repo stays in the merging state. */
    public void merge(Path repo, String branch, MergeMode mode) { throw todo(); }

    /** Rebases the current branch onto {@code onto}. */
    public void rebase(Path repo, String onto) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
