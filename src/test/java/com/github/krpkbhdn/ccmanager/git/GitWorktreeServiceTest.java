package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorktreeServiceTest {

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private GitWorktreeService service;
    private Path target;

    @BeforeEach
    void prepare() {
        sandbox = GitCliSandbox.create(root);
        service = new GitWorktreeService(sandbox.runner);
        sandbox.write("a.txt", "base\n");
        sandbox.commitAll("base");
        target = root.resolve("wt-feature");
    }

    private Worktree linked() {
        return service.list(sandbox.repo).stream().filter(w -> !w.main()).findFirst().orElseThrow();
    }

    @Test
    void addCreatesBranchAndListShowsMainFirstThenLinkedAsNotMerged() {
        service.add(sandbox.repo, "feature/x", "", target);

        var all = service.list(sandbox.repo);
        assertThat(all).hasSize(2);
        assertThat(all.getFirst().main()).isTrue();
        assertThat(all.getFirst().branch()).isEqualTo("main");
        assertThat(all.getFirst().merged()).isFalse();
        assertThat(linked().branch()).isEqualTo("feature/x");
        assertThat(linked().merged()).isFalse();
        assertThat(linked().dirty()).isFalse();
    }

    @Test
    void branchWithOwnCommitIsNotMergedAndUntrackedFileMakesItDirty() throws Exception {
        service.add(sandbox.repo, "feature", "main", target);
        Files.writeString(target.resolve("b.txt"), "b");
        sandbox.runIn(target, "add", "-A");
        sandbox.runIn(target, "commit", "-m", "own");
        assertThat(linked().merged()).isFalse();

        Files.writeString(target.resolve("c.txt"), "c");
        assertThat(linked().dirty()).isTrue();
    }

    @Test
    void branchMergedIntoAdvancedMainIsMerged() throws Exception {
        service.add(sandbox.repo, "feature", "main", target);
        Files.writeString(target.resolve("b.txt"), "b");
        sandbox.runIn(target, "add", "-A");
        sandbox.runIn(target, "commit", "-m", "own");
        sandbox.git("merge", "--ff-only", "feature");
        sandbox.write("a.txt", "later\n");
        sandbox.commitAll("later");

        assertThat(linked().merged()).isTrue();
    }

    @Test
    void addRejectsInvalidBranchAndTargetWithoutRunningGit() {
        assertThatThrownBy(() -> service.add(sandbox.repo, "bad name", "", target)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(sandbox.repo, "ok", "", Path.of("relative"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeCleanWorktreeKeepsBranch() {
        service.add(sandbox.repo, "feature", "", target);

        service.remove(sandbox.repo, target, false);

        assertThat(service.list(sandbox.repo)).hasSize(1);
        assertThat(sandbox.git("branch", "--list", "feature")).contains("feature");
    }

    @Test
    void removeDirtyWorktreeNeedsForce() throws Exception {
        service.add(sandbox.repo, "feature", "", target);
        Files.writeString(target.resolve("dirty.txt"), "x");

        assertThatThrownBy(() -> service.remove(sandbox.repo, target, false))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.DIRTY_TREE));
        service.remove(sandbox.repo, target, true);

        assertThat(service.list(sandbox.repo)).hasSize(1);
    }

    @Test
    void removeRefusesMainWorktree() {
        assertThatThrownBy(() -> service.remove(sandbox.repo, sandbox.repo, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
