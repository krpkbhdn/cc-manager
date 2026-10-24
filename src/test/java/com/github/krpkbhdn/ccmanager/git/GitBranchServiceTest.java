package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitBranchServiceTest {

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private GitBranchService service;

    private void prepare() {
        sandbox = GitCliSandbox.create(root);
        service = new GitBranchService(sandbox.runner);
        sandbox.write("a.txt", "base\n");
        sandbox.commitAll("base");
    }

    private void prepareWithOrigin() {
        sandbox = GitCliSandbox.createWithOrigin(root);
        service = new GitBranchService(sandbox.runner);
    }

    private String currentBranch() {
        return sandbox.git("rev-parse", "--abbrev-ref", "HEAD").strip();
    }

    private void commitOnBranch(String branch, String file, String content) {
        sandbox.git("checkout", "-q", "-B", branch);
        sandbox.write(file, content);
        sandbox.commitAll(branch + " change");
    }

    private GitException failureOf(Runnable action) {
        return catchThrowableOfType(GitException.class, action::run);
    }

    @Test
    void listsCurrentFirstWithUpstreamTrackingAndSkipsRemoteHead() {
        prepareWithOrigin();
        sandbox.git("remote", "set-head", "origin", "main");
        sandbox.git("branch", "zeta");
        sandbox.write("b.txt", "b\n");
        sandbox.commitAll("ahead of origin");

        var branches = service.branches(sandbox.repo);

        assertThat(branches).extracting(GitBranch::name).containsExactly("main", "zeta", "origin/main");
        var main = branches.getFirst();
        assertThat(main.current()).isTrue();
        assertThat(main.upstream()).isEqualTo("origin/main");
        assertThat(main.ahead()).isEqualTo(1);
        assertThat(main.behind()).isZero();
        assertThat(main.subject()).isEqualTo("ahead of origin");
        assertThat(main.date()).isNotNull();
        assertThat(branches.getLast().remote()).isTrue();
    }

    @Test
    void emptyRepositoryHasNoBranches() {
        sandbox = GitCliSandbox.create(root);
        service = new GitBranchService(sandbox.runner);

        assertThat(service.branches(sandbox.repo)).isEmpty();
    }

    @Test
    void createsAndChecksOutBranchesFromHeadOrStartPoint() {
        prepare();
        var base = sandbox.git("rev-parse", "HEAD").strip();
        sandbox.write("b.txt", "b\n");
        sandbox.commitAll("second");

        service.create(sandbox.repo, "from-base", base, false);
        service.create(sandbox.repo, "feature/x", null, true);

        assertThat(sandbox.git("rev-parse", "from-base").strip()).isEqualTo(base);
        assertThat(currentBranch()).isEqualTo("feature/x");
    }

    @Test
    void rejectsInvalidBranchNames() {
        prepare();

        for (var name : List.of("-x", "--orphan", "bad name", "a..b", "", "end.lock")) {
            assertThat(failureOf(() -> service.create(sandbox.repo, name, null, false))).as(name).isNotNull();
        }
        assertThat(service.branches(sandbox.repo)).extracting(GitBranch::name).containsExactly("main");
    }

    @Test
    void checkoutOfRemoteNameCreatesTrackingBranch() {
        prepareWithOrigin();
        var other = sandbox.cloneOrigin("other");
        other.git("checkout", "-b", "feature");
        other.write("f.txt", "f\n");
        other.commitAll("feature");
        other.git("push", "-u", "origin", "feature");
        sandbox.git("fetch");

        service.checkout(sandbox.repo, "origin/feature", false);

        assertThat(currentBranch()).isEqualTo("feature");
        assertThat(sandbox.git("rev-parse", "--abbrev-ref", "feature@{upstream}").strip()).isEqualTo("origin/feature");
    }

    @Test
    void checkoutWithoutAutoStashFailsOnConflictingLocalChanges() {
        prepare();
        commitOnBranch("other", "a.txt", "other\n");
        sandbox.git("checkout", "main");
        sandbox.write("a.txt", "dirty\n");

        assertThat(failureOf(() -> service.checkout(sandbox.repo, "other", false)).kind()).isEqualTo(Kind.DIRTY_TREE);
        assertThat(currentBranch()).isEqualTo("main");
    }

    @Test
    void autoStashCarriesTrackedAndUntrackedChangesToTheOtherBranch() {
        prepare();
        sandbox.git("branch", "other");
        sandbox.write("a.txt", "dirty\n");
        sandbox.write("untracked.txt", "u\n");

        service.checkout(sandbox.repo, "other", true);

        assertThat(currentBranch()).isEqualTo("other");
        assertThat(sandbox.read("a.txt")).isEqualTo("dirty\n");
        assertThat(sandbox.read("untracked.txt")).isEqualTo("u\n");
        assertThat(sandbox.git("stash", "list")).isBlank();
    }

    @Test
    void autoStashWithCleanTreeDoesNotPopForeignStashes() {
        prepare();
        sandbox.write("keep.txt", "k\n");
        sandbox.git("add", "keep.txt");
        sandbox.git("stash");
        sandbox.git("branch", "other");

        service.checkout(sandbox.repo, "other", true);

        assertThat(sandbox.git("stash", "list").lines()).hasSize(1);
    }

    @Test
    void autoStashConflictIsSurfacedAndKeepsTheStash() {
        prepare();
        commitOnBranch("other", "a.txt", "other\n");
        sandbox.git("checkout", "main");
        sandbox.write("a.txt", "dirty\n");

        var failure = failureOf(() -> service.checkout(sandbox.repo, "other", true));

        assertThat(failure.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(currentBranch()).isEqualTo("other");
        assertThat(sandbox.git("stash", "list").lines()).hasSize(1);
    }

    @Test
    void failedCheckoutRestoresAutoStashedChanges() {
        prepare();
        sandbox.write("a.txt", "dirty\n");

        var failure = failureOf(() -> service.checkout(sandbox.repo, "does-not-exist", true));

        assertThat(failure.kind()).isEqualTo(Kind.NOT_FOUND);
        assertThat(sandbox.read("a.txt")).isEqualTo("dirty\n");
        assertThat(sandbox.git("stash", "list")).isBlank();
    }

    @Test
    void checkoutCommitDetachesHead() {
        prepare();
        var first = sandbox.git("rev-parse", "HEAD").strip();
        sandbox.write("b.txt", "b\n");
        sandbox.commitAll("second");

        service.checkoutCommit(sandbox.repo, first);

        assertThat(currentBranch()).isEqualTo("HEAD");
        assertThat(sandbox.git("rev-parse", "HEAD").strip()).isEqualTo(first);
        assertThat(failureOf(() -> service.checkoutCommit(sandbox.repo, "--orphan"))).isNotNull();
    }

    @Test
    void renamesDeletesAndRefusesToDeleteTheCheckedOutBranch() {
        prepare();
        sandbox.git("branch", "old");
        commitOnBranch("unmerged", "u.txt", "u\n");
        sandbox.git("checkout", "main");

        service.rename(sandbox.repo, "old", "new");
        var refused = failureOf(() -> service.delete(sandbox.repo, "main", true));
        var notMerged = failureOf(() -> service.delete(sandbox.repo, "unmerged", false));
        service.delete(sandbox.repo, "unmerged", true);
        service.delete(sandbox.repo, "new", false);

        assertThat(refused.getMessage()).contains("checked-out");
        assertThat(notMerged.getMessage()).contains("not fully merged");
        assertThat(service.branches(sandbox.repo)).extracting(GitBranch::name).containsExactly("main");
    }

    @Test
    void deletesRemoteBranchAndSetsUpstream() {
        prepareWithOrigin();
        sandbox.git("push", "origin", "main:gone");
        sandbox.git("fetch");
        sandbox.git("branch", "topic");

        service.setUpstream(sandbox.repo, "topic", "origin/main");
        service.deleteRemote(sandbox.repo, "origin", "gone");
        sandbox.git("fetch", "--prune");

        assertThat(sandbox.git("rev-parse", "--abbrev-ref", "topic@{upstream}").strip()).isEqualTo("origin/main");
        assertThat(service.branches(sandbox.repo)).extracting(GitBranch::name).doesNotContain("origin/gone");
    }

    @Test
    void defaultMergeFastForwardsWhenPossibleAndNoFastForwardMakesACommit() {
        prepare();
        commitOnBranch("topic", "t.txt", "t\n");
        sandbox.git("checkout", "main");

        service.merge(sandbox.repo, "topic", MergeMode.DEFAULT);
        assertThat(sandbox.git("rev-list", "--merges", "--count", "HEAD").strip()).isEqualTo("0");
        sandbox.git("reset", "--hard", "HEAD~1");
        service.merge(sandbox.repo, "topic", MergeMode.NO_FAST_FORWARD);

        assertThat(sandbox.git("rev-list", "--merges", "--count", "HEAD").strip()).isEqualTo("1");
    }

    @Test
    void fastForwardOnlyRefusesDivergedBranches() {
        prepare();
        commitOnBranch("topic", "t.txt", "t\n");
        sandbox.git("checkout", "main");
        sandbox.write("m.txt", "m\n");
        sandbox.commitAll("main moves");

        assertThat(failureOf(() -> service.merge(sandbox.repo, "topic", MergeMode.FAST_FORWARD_ONLY)).kind()).isEqualTo(Kind.NON_FAST_FORWARD);
    }

    @Test
    void squashMergeStagesChangesWithoutCommitting() {
        prepare();
        commitOnBranch("topic", "t.txt", "t\n");
        sandbox.git("checkout", "main");
        var before = sandbox.git("rev-parse", "HEAD");

        service.merge(sandbox.repo, "topic", MergeMode.SQUASH);

        assertThat(sandbox.git("rev-parse", "HEAD")).isEqualTo(before);
        assertThat(sandbox.git("diff", "--cached", "--name-only").strip()).isEqualTo("t.txt");
    }

    @Test
    void mergeConflictRaisesConflictAndLeavesTheRepositoryMerging() {
        prepare();
        commitOnBranch("topic", "a.txt", "topic\n");
        sandbox.git("checkout", "main");
        sandbox.write("a.txt", "main\n");
        sandbox.commitAll("main edit");

        var failure = failureOf(() -> service.merge(sandbox.repo, "topic", MergeMode.DEFAULT));

        assertThat(failure.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(Files.exists(sandbox.repo.resolve(".git/MERGE_HEAD"))).isTrue();
    }

    @Test
    void rebasesOntoAnotherBranchAndReportsConflicts() {
        prepare();
        commitOnBranch("topic", "t.txt", "t\n");
        sandbox.git("checkout", "main");
        sandbox.write("m.txt", "m\n");
        sandbox.commitAll("main moves");
        sandbox.git("checkout", "topic");

        service.rebase(sandbox.repo, "main");

        assertThat(sandbox.git("log", "--format=%s", "-3").lines()).containsExactly("topic change", "main moves", "base");

        sandbox.git("checkout", "main");
        sandbox.write("t.txt", "conflicting\n");
        sandbox.commitAll("conflicting");
        sandbox.git("checkout", "topic");

        var failure = failureOf(() -> service.rebase(sandbox.repo, "main"));

        assertThat(failure.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(Files.exists(sandbox.repo.resolve(".git/rebase-merge"))).isTrue();
    }
}
