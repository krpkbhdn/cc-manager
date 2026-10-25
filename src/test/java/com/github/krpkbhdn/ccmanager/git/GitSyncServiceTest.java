package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSyncServiceTest {

    private static final PushOptions PLAIN = new PushOptions(false, false, false);

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private GitCliSandbox other;
    private GitSyncService service;

    private void prepare() {
        sandbox = GitCliSandbox.createWithOrigin(root);
        other = sandbox.cloneOrigin("other");
        service = new GitSyncService(sandbox.runner);
    }

    private void pushFromOther(String file, String content) {
        other.write(file, content);
        other.commitAll("other " + file);
        other.git("push");
    }

    private void commitLocally(String file, String content) {
        sandbox.write(file, content);
        sandbox.commitAll("local " + file);
    }

    private String originMain() {
        return sandbox.runIn(root, "--git-dir", root.resolve("origin.git").toString(), "rev-parse", "main").strip();
    }

    private GitException failureOf(Runnable action) {
        return catchThrowableOfType(GitException.class, action::run);
    }

    @Test
    void fetchBringsInRemoteCommitsAndPrunesDeletedBranches() {
        prepare();
        pushFromOther("r.txt", "r\n");
        other.git("push", "origin", "main:doomed");
        sandbox.git("fetch");
        other.git("push", "origin", "--delete", "doomed");
        var progress = new ArrayList<String>();

        service.fetch(sandbox.repo, progress::add);

        assertThat(sandbox.git("rev-parse", "origin/main").strip()).isEqualTo(originMain());
        assertThat(sandbox.git("branch", "-r")).doesNotContain("doomed");
    }

    @Test
    void pullFastForwardsAndMerges() {
        prepare();
        pushFromOther("r.txt", "r\n");

        service.pull(sandbox.repo, PullMode.FAST_FORWARD_ONLY, null);
        assertThat(sandbox.repo.resolve("r.txt")).exists();

        commitLocally("l.txt", "l\n");
        pushFromOther("r2.txt", "r2\n");
        service.pull(sandbox.repo, PullMode.MERGE, null);

        assertThat(sandbox.git("rev-list", "--merges", "--count", "HEAD").strip()).isEqualTo("1");
    }

    @Test
    void pullRebaseReplaysLocalCommitsOnTop() {
        prepare();
        commitLocally("l.txt", "l\n");
        pushFromOther("r.txt", "r\n");

        service.pull(sandbox.repo, PullMode.REBASE, null);

        assertThat(sandbox.git("log", "--format=%s", "-2").lines()).containsExactly("local l.txt", "other r.txt");
    }

    @Test
    void fastForwardOnlyPullOfDivergedBranchIsRejected() {
        prepare();
        commitLocally("l.txt", "l\n");
        pushFromOther("r.txt", "r\n");

        assertThat(failureOf(() -> service.pull(sandbox.repo, PullMode.FAST_FORWARD_ONLY, null)).kind()).isEqualTo(Kind.NON_FAST_FORWARD);
    }

    @Test
    void conflictingPullRaisesConflict() {
        prepare();
        commitLocally("README.md", "local\n");
        pushFromOther("README.md", "remote\n");

        var failure = failureOf(() -> service.pull(sandbox.repo, PullMode.MERGE, null));

        assertThat(failure.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(sandbox.git("status", "--porcelain")).contains("UU README.md");
    }

    @Test
    void pullWithoutUpstreamIsNoUpstream() {
        prepare();
        sandbox.git("checkout", "-b", "lonely");

        assertThat(failureOf(() -> service.pull(sandbox.repo, PullMode.MERGE, null)).kind()).isEqualTo(Kind.NO_UPSTREAM);
    }

    @Test
    void pushSendsTheCurrentBranch() {
        prepare();
        commitLocally("l.txt", "l\n");

        service.push(sandbox.repo, PLAIN, null);

        assertThat(originMain()).isEqualTo(sandbox.git("rev-parse", "HEAD").strip());
    }

    @Test
    void pushWithoutUpstreamFailsUntilSetUpstreamIsRequested() {
        prepare();
        sandbox.git("checkout", "-b", "feature");
        commitLocally("f.txt", "f\n");

        assertThat(failureOf(() -> service.push(sandbox.repo, PLAIN, null)).kind()).isEqualTo(Kind.NO_UPSTREAM);
        service.push(sandbox.repo, new PushOptions(false, true, false), null);

        assertThat(sandbox.git("rev-parse", "--abbrev-ref", "feature@{upstream}").strip()).isEqualTo("origin/feature");
    }

    @Test
    void forceWithLeaseIsRefusedUntilTheFetchedRemoteCommitIsPartOfTheLocalHistory() {
        prepare();
        commitLocally("l.txt", "l\n");
        pushFromOther("r.txt", "r\n");

        assertThat(failureOf(() -> service.push(sandbox.repo, PLAIN, null)).kind()).isEqualTo(Kind.NON_FAST_FORWARD);
        var staleLease = failureOf(() -> service.push(sandbox.repo, new PushOptions(true, false, false), null));
        service.fetch(sandbox.repo, null);
        var fetchedButNotIncluded = failureOf(() -> service.push(sandbox.repo, new PushOptions(true, false, false), null));
        sandbox.git("merge", "--no-edit", "origin/main");
        service.push(sandbox.repo, new PushOptions(true, false, false), null);

        assertThat(staleLease.kind()).isEqualTo(Kind.NON_FAST_FORWARD);
        assertThat(fetchedButNotIncluded).isNotNull();
        assertThat(originMain()).isEqualTo(sandbox.git("rev-parse", "HEAD").strip());
    }

    @Test
    void pushWithTagsSendsThem() {
        prepare();
        sandbox.git("tag", "v1");

        service.push(sandbox.repo, new PushOptions(false, false, true), null);

        assertThat(sandbox.runIn(root, "--git-dir", root.resolve("origin.git").toString(), "tag")).contains("v1");
    }
}
