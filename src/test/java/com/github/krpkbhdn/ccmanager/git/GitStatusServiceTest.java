package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.git.GitStatus.Change;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Operation;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitStatusServiceTest {

    @TempDir
    Path root;

    private GitStatusService serviceFor(GitCliSandbox sandbox) {
        return new GitStatusService(sandbox.runner);
    }

    private GitCliSandbox divergedOnSameFile() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "base\n");
        sandbox.commitAll("base");
        sandbox.git("checkout", "-b", "other");
        sandbox.write("a.txt", "other\n");
        sandbox.commitAll("other");
        sandbox.git("checkout", "main");
        sandbox.write("a.txt", "main\n");
        sandbox.commitAll("main");
        return sandbox;
    }

    private static Change change(GitStatus status, String path) {
        return status.changes().stream().filter(c -> c.path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void aMissingProjectFolderIsNotARepository() {
        var sandbox = GitCliSandbox.create(root);

        assertThat(serviceFor(sandbox).status(root.resolve("deleted-project")).repository()).isFalse();
    }

    @Test
    void nonRepositoryIsReportedWithoutException() {
        var sandbox = GitCliSandbox.create(root);

        assertThat(serviceFor(sandbox).status(root).repository()).isFalse();
    }

    @Test
    void emptyRepositoryWithoutCommitsWorks() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(status.repository()).isTrue();
        assertThat(status.branch()).isEqualTo("main");
        assertThat(status.head()).isNull();
        assertThat(status.changes()).containsExactly(new Change("a.txt", null, Kind.NONE, Kind.UNTRACKED));
    }

    @Test
    void reportsStagedUnstagedUntrackedDeletedAndRenamedEntries() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("keep.txt", "1\n2\n3\n");
        sandbox.write("gone.txt", "bye\n");
        sandbox.write("old name.txt", "rename me\nwith content\nthat is long enough\n");
        sandbox.commitAll("base");
        sandbox.write("keep.txt", "1\n2\n3\n4\n");
        sandbox.git("add", "keep.txt");
        sandbox.write("keep.txt", "1\n2\n3\n4\n5\n");
        sandbox.git("rm", "-q", "gone.txt");
        sandbox.git("mv", "old name.txt", "new name.txt");
        sandbox.write("dir/new.txt", "n\n");

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(change(status, "keep.txt")).extracting(Change::staged, Change::unstaged).containsExactly(Kind.MODIFIED, Kind.MODIFIED);
        assertThat(change(status, "gone.txt").staged()).isEqualTo(Kind.DELETED);
        assertThat(change(status, "new name.txt")).extracting(Change::oldPath, Change::staged).containsExactly("old name.txt", Kind.RENAMED);
        assertThat(change(status, "dir/new.txt").unstaged()).isEqualTo(Kind.UNTRACKED);
    }

    @Test
    void reportsAheadBehindAndUpstream() {
        var sandbox = GitCliSandbox.createWithOrigin(root);
        var other = sandbox.cloneOrigin("other");
        other.write("remote.txt", "r\n");
        other.commitAll("remote");
        other.git("push");
        sandbox.write("local.txt", "l\n");
        sandbox.commitAll("local");
        sandbox.git("fetch");

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(status.upstream()).isEqualTo("origin/main");
        assertThat(status.ahead()).isEqualTo(1);
        assertThat(status.behind()).isEqualTo(1);
    }

    @Test
    void detachedHeadHasNoBranch() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        var commit = sandbox.commitAll("a");
        sandbox.git("checkout", "--detach", commit);

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(status.detached()).isTrue();
        assertThat(status.branch()).isNull();
        assertThat(status.head()).isEqualTo(commit);
    }

    @Test
    void mergeConflictIsAnUnmergedChangeWithOperation() {
        var sandbox = divergedOnSameFile();
        sandbox.runner.tryRun(sandbox.repo, "merge", "other");

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(status.operation()).isEqualTo(Operation.MERGE);
        assertThat(change(status, "a.txt").conflicted()).isTrue();
    }

    @Test
    void stoppedRebaseIsDetectedAndStashesAreCounted() {
        var sandbox = divergedOnSameFile();
        sandbox.write("b.txt", "b\n");
        sandbox.git("add", "b.txt");
        sandbox.git("stash");
        sandbox.git("checkout", "other");
        sandbox.runner.tryRun(sandbox.repo, "rebase", "main");

        var status = serviceFor(sandbox).status(sandbox.repo);

        assertThat(status.operation()).isEqualTo(Operation.REBASE);
        assertThat(status.stashCount()).isEqualTo(1);
    }

    @Test
    void statusWorksInsideALinkedWorktree() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("a");
        var linked = root.resolve("linked");
        sandbox.git("worktree", "add", "-b", "feature", linked.toString());

        var status = serviceFor(sandbox).status(linked);

        assertThat(status.repository()).isTrue();
        assertThat(status.branch()).isEqualTo("feature");
        assertThat(status.operation()).isEqualTo(Operation.NONE);
    }

    @Test
    void diffSeparatesStagedFromUnstaged() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "1\n2\n3\n");
        sandbox.commitAll("base");
        sandbox.write("a.txt", "1\n2\n3\nstaged\n");
        sandbox.git("add", "a.txt");
        sandbox.write("a.txt", "1\n2\n3\nstaged\nunstaged\n");
        var service = serviceFor(sandbox);

        var staged = service.diff(sandbox.repo, "a.txt", true);
        var unstaged = service.diff(sandbox.repo, "a.txt", false);

        assertThat(staged.lines()).filteredOn(l -> l.kind() == LineKind.ADDED).extracting(l -> l.text()).containsExactly("staged");
        assertThat(unstaged.lines()).filteredOn(l -> l.kind() == LineKind.ADDED).extracting(l -> l.text()).containsExactly("unstaged");
    }

    @Test
    void untrackedFileIsAWholeFileAddition() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("a");
        sandbox.write("new.txt", "one\ntwo\n");

        var diff = serviceFor(sandbox).diff(sandbox.repo, "new.txt", false);

        assertThat(diff.change()).isEqualTo(ChangeType.UNTRACKED);
        assertThat(diff.path()).isEqualTo("new.txt");
        assertThat(diff.additions()).isEqualTo(2);
    }

    @Test
    void untrackedBinaryAndOversizedFilesCarryNoLines() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("a");
        sandbox.writeBytes("blob.bin", new byte[] {1, 0, 2, 0, 3});
        sandbox.writeBytes("big.txt", new byte[(int) sandbox.runner.properties().maxUntrackedBytes() + 1]);
        var service = serviceFor(sandbox);

        assertThat(service.diff(sandbox.repo, "blob.bin", false).binary()).isTrue();
        assertThat(service.diff(sandbox.repo, "big.txt", false).lines()).extracting(l -> l.text()).containsExactly(GitStatusService.TOO_LARGE_MARKER);
    }

    @Test
    void stagedDiffInRepositoryWithoutCommitsShowsNewFile() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "a\n");
        sandbox.git("add", "a.txt");

        var diff = serviceFor(sandbox).diff(sandbox.repo, "a.txt", true);

        assertThat(diff.change()).isEqualTo(ChangeType.ADDED);
    }

    @Test
    void remotesAndIdentity() {
        var sandbox = GitCliSandbox.createWithOrigin(root);
        var service = serviceFor(sandbox);

        assertThat(service.remotes(sandbox.repo)).singleElement().satisfies(remote -> {
            assertThat(remote.name()).isEqualTo("origin");
            assertThat(remote.fetchUrl()).isEqualTo(remote.pushUrl()).isNotBlank();
        });
        assertThat(service.identity(sandbox.repo).name()).isEqualTo("Test User");
        sandbox.git("config", "--unset", "user.email");
        assertThat(service.identity(sandbox.repo).email()).isNull();
    }
}
