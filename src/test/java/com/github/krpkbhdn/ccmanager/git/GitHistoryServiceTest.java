package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.ResetMode;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHistoryServiceTest {

    private final GitHistoryService service = new GitHistoryService(new GitRunner());
    private HistoryTestRepo repo;
    private String first;

    @BeforeEach
    void setUp(@TempDir Path directory) {
        assumeTrue(HistoryTestRepo.gitAvailable());
        repo = HistoryTestRepo.init(directory);
        first = repo.commit("a.txt", "1\n", "first");
        repo.commit("a.txt", "2\n", "second");
    }

    @Test
    void softResetKeepsChangesStagedMixedUnstagesHardDiscards() {
        service.reset(repo.root(), first, ResetMode.SOFT);
        assertThat(repo.git("status", "--porcelain")).isEqualTo("M  a.txt");

        service.reset(repo.root(), "HEAD", ResetMode.MIXED);
        assertThat(repo.git("status", "--porcelain")).isEqualTo("M a.txt");

        service.reset(repo.root(), "HEAD", ResetMode.HARD);
        assertThat(repo.git("status", "--porcelain")).isEmpty();
        assertThat(repo.read("a.txt")).isEqualTo("1\n");
    }

    @Test
    void revertCreatesACommitUndoingTheChange() {
        service.revert(repo.root(), "HEAD");

        assertThat(repo.read("a.txt")).isEqualTo("1\n");
        assertThat(repo.git("log", "-1", "--format=%s")).startsWith("Revert");
    }

    @Test
    void revertOfAMergeCommitUsesTheFirstParentAsMainline() {
        repo.git("checkout", "-q", "-b", "feature");
        repo.commit("f.txt", "f\n", "feature work");
        repo.git("checkout", "-q", "main");
        repo.commit("m.txt", "m\n", "main work");
        repo.git("merge", "-q", "--no-ff", "-m", "merge feature", "feature");

        service.revert(repo.root(), "HEAD");

        assertThat(repo.root().resolve("f.txt")).doesNotExist();
        assertThat(repo.root().resolve("m.txt")).exists();
    }

    @Test
    void cherryPickAppliesACommitFromAnotherBranch() {
        repo.git("checkout", "-q", "-b", "feature", first);
        var picked = repo.commit("f.txt", "f\n", "feature work");
        repo.git("checkout", "-q", "main");

        service.cherryPick(repo.root(), picked);

        assertThat(repo.read("f.txt")).isEqualTo("f\n");
        assertThat(repo.git("log", "-1", "--format=%s")).isEqualTo("feature work");
    }

    @Test
    void conflictingCherryPickThrowsConflictAndLeavesTheStoppedState() {
        repo.git("checkout", "-q", "-b", "feature", first);
        var picked = repo.commit("a.txt", "other\n", "conflicting");
        repo.git("checkout", "-q", "main");

        assertThatThrownBy(() -> service.cherryPick(repo.root(), picked))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
        assertThat(repo.root().resolve(".git/CHERRY_PICK_HEAD")).exists();
    }

    @Test
    void tagsDistinguishAnnotatedFromLightweightAndResolveTheCommit() {
        var head = repo.git("rev-parse", "HEAD");
        service.createTag(repo.root(), "light", first, null);
        service.createTag(repo.root(), "v1", null, "release one");

        var tags = service.tags(repo.root());

        assertThat(tags).hasSize(2);
        assertThat(tags).filteredOn(tag -> tag.name().equals("light")).singleElement().satisfies(tag -> {
            assertThat(tag.annotated()).isFalse();
            assertThat(tag.commit()).isEqualTo(first);
            assertThat(tag.message()).isNull();
        });
        assertThat(tags).filteredOn(tag -> tag.name().equals("v1")).singleElement().satisfies(tag -> {
            assertThat(tag.annotated()).isTrue();
            assertThat(tag.commit()).isEqualTo(head);
            assertThat(tag.message()).isEqualTo("release one");
        });
    }

    @Test
    void deleteTagRemovesItAndOptionLikeNamesAreRejected() {
        service.createTag(repo.root(), "gone", null, null);

        service.deleteTag(repo.root(), "gone");

        assertThat(service.tags(repo.root())).isEmpty();
        assertThatThrownBy(() -> service.createTag(repo.root(), "--force", null, null)).isInstanceOf(GitException.class);
    }
}
