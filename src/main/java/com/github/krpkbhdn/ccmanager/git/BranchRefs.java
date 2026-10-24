package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.nio.file.Path;
import java.util.Optional;

/** Branch-name checks and lookups shared by the branch and sync services. */
final class BranchRefs {

    private final GitRunner git;

    BranchRefs(GitRunner git) {
        this.git = git;
    }

    /** The checked-out branch; empty when HEAD is detached. */
    Optional<String> current(Path repo) {
        var result = git.tryRun(repo, "symbolic-ref", "--short", "-q", "HEAD");
        var name = result.stdout().strip();
        return result.ok() && !name.isEmpty() ? Optional.of(name) : Optional.empty();
    }

    /** A name for a branch that is about to be created. */
    void requireValidNewName(Path repo, String name) {
        requireNotOptionLike(name);
        if (!git.tryRun(repo, "check-ref-format", "--branch", name).ok()) {
            throw new GitException(Kind.OTHER, "'" + name + "' is not a valid branch name", "");
        }
    }

    /** Any ref, remote or commit-ish typed by the user must not be mistaken for a git option. */
    void requireNotOptionLike(String name) {
        if (name == null || name.isBlank() || name.startsWith("-")) {
            throw new GitException(Kind.OTHER, "'" + name + "' is not a valid name", "");
        }
    }

    boolean exists(Path repo, String fullRef) {
        return git.tryRun(repo, "show-ref", "--verify", "--quiet", fullRef).ok();
    }
}
