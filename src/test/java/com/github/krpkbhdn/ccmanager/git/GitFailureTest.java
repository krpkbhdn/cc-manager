package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitFailureTest {

    @TempDir
    Path root;

    private GitException failureOf(GitCliSandbox sandbox, String... args) {
        return catchThrowableOfType(GitException.class, () -> sandbox.runner.out(sandbox.repo, args));
    }

    private GitCliSandbox sandboxWithCommit() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "base\n");
        sandbox.commitAll("base");
        return sandbox;
    }

    @Test
    void emptyCommitIsNothingToCommit() {
        var sandbox = sandboxWithCommit();

        assertThat(failureOf(sandbox, "commit", "-m", "again").kind()).isEqualTo(Kind.NOTHING_TO_COMMIT);
    }

    @Test
    void missingIdentityGetsAHint() {
        var sandbox = sandboxWithCommit();
        sandbox.write("a.txt", "changed\n");
        sandbox.git("add", "-A");

        var failure = failureOf(sandbox, "-c", "user.name=", "-c", "user.email=", "commit", "-m", "x");

        assertThat(failure.kind()).isEqualTo(Kind.IDENTITY_UNKNOWN);
        assertThat(failure.getMessage()).isEqualTo(GitFailure.IDENTITY_HINT);
    }

    @Test
    void mergeConflictIsAConflictWithTheConflictLineAsHeadline() {
        var sandbox = sandboxWithCommit();
        sandbox.git("checkout", "-b", "other");
        sandbox.write("a.txt", "other\n");
        sandbox.commitAll("other");
        sandbox.git("checkout", GitCliSandbox.MAIN);
        sandbox.write("a.txt", "main\n");
        sandbox.commitAll("main");

        var failure = failureOf(sandbox, "merge", "other");

        assertThat(failure.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(failure.getMessage()).startsWith("CONFLICT (");
    }

    @Test
    void rejectedPushIsNonFastForward() {
        var sandbox = GitCliSandbox.createWithOrigin(root);
        var other = sandbox.cloneOrigin("other");
        other.write("b.txt", "b\n");
        other.commitAll("b");
        other.git("push");
        sandbox.write("c.txt", "c\n");
        sandbox.commitAll("c");

        assertThat(failureOf(sandbox, "push").kind()).isEqualTo(Kind.NON_FAST_FORWARD);
    }

    @Test
    void pullWithoutTrackingBranchIsNoUpstream() {
        var sandbox = GitCliSandbox.createWithOrigin(root);
        sandbox.git("checkout", "-b", "lonely");

        assertThat(failureOf(sandbox, "pull", "--no-rebase").kind()).isEqualTo(Kind.NO_UPSTREAM);
    }

    @Test
    void unknownRevisionIsNotFound() {
        var sandbox = sandboxWithCommit();

        assertThat(failureOf(sandbox, "checkout", "nope", "--").kind()).isEqualTo(Kind.NOT_FOUND);
    }

    @Test
    void existingIndexLockIsLocked() throws Exception {
        var sandbox = sandboxWithCommit();
        Files.createFile(sandbox.repo.resolve(".git/index.lock"));
        sandbox.write("b.txt", "b\n");

        assertThat(failureOf(sandbox, "add", "b.txt").kind()).isEqualTo(Kind.LOCKED);
    }

    @Test
    void checkoutOverLocalChangesIsADirtyTree() {
        var sandbox = sandboxWithCommit();
        sandbox.git("checkout", "-b", "other");
        sandbox.write("a.txt", "other\n");
        sandbox.commitAll("other");
        sandbox.git("checkout", GitCliSandbox.MAIN);
        sandbox.write("a.txt", "dirty\n");

        assertThat(failureOf(sandbox, "checkout", "other").kind()).isEqualTo(Kind.DIRTY_TREE);
    }

    @Test
    void subcommandSkipsTheValueOfEachDashC() {
        assertThat(GitFailure.subcommand(List.of("-c", "core.editor=true", "rebase", "--continue"))).isEqualTo("rebase");
        assertThat(GitFailure.subcommand(List.of("--literal-pathspecs", "status"))).isEqualTo("status");
        assertThat(GitFailure.subcommand(List.of())).isEqualTo("command");
    }

    @Test
    void textsThatCannotBeReproducedOfflineAreClassifiedByMarker() {
        Consumer<String> authFailure = text -> assertThat(GitFailure.classify(text)).isEqualTo(Kind.AUTH);

        authFailure.accept("fatal: Authentication failed for 'https://example.com/x.git/'");
        authFailure.accept("fatal: could not read Username for 'https://example.com': terminal prompts disabled");
        authFailure.accept("git@example.com: Permission denied (publickey).");
        assertThat(GitFailure.message(Kind.AUTH, "fatal: Authentication failed")).isEqualTo(GitFailure.AUTH_HINT);
        assertThat(GitFailure.classify("remote: error: pre-receive hook declined\n ! [remote rejected] main -> main\nerror: failed to push some refs"))
                .isEqualTo(Kind.HOOK_FAILED);
        assertThat(GitFailure.classify("something unexpected")).isEqualTo(Kind.OTHER);
    }
}
