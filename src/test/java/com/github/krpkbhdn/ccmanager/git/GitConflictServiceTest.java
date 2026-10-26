package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitConflictServiceTest {

    private final GitConflictService service = new GitConflictService(new GitRunner());
    private HistoryTestRepo repo;

    @BeforeEach
    void setUp(@TempDir Path directory) {
        assumeTrue(HistoryTestRepo.gitAvailable());
        repo = HistoryTestRepo.init(directory);
        repo.commit("a.txt", "base\n", "base");
        repo.git("checkout", "-q", "-b", "feature");
        repo.commit("a.txt", "feature\n", "feature change");
        repo.git("checkout", "-q", "main");
        repo.commit("a.txt", "main\n", "main change");
    }

    private void conflictingMerge() {
        assertThatThrownBy(() -> repo.git("merge", "feature")).isInstanceOf(IllegalStateException.class);
    }

    private void conflictingRebase() {
        repo.git("checkout", "-q", "feature");
        assertThatThrownBy(() -> repo.git("rebase", "main")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duringAMergeOursIsTheCurrentBranch() {
        conflictingMerge();

        service.resolve(repo.root(), "a.txt", ConflictSide.OURS);

        assertThat(repo.read("a.txt")).isEqualTo("main\n");
    }

    @Test
    void duringAMergeTheirsIsIncoming() {
        conflictingMerge();

        service.resolve(repo.root(), "a.txt", ConflictSide.THEIRS);
        assertThat(repo.read("a.txt")).isEqualTo("feature\n");
    }

    @Test
    void aFileNameWithGlobCharactersResolvesOnlyThatFile() {
        repo.commit("[id].tsx", "main\n", "main route");
        repo.commit("i.tsx", "i\n", "other file");
        repo.git("checkout", "-q", "feature");
        repo.commit("[id].tsx", "feature\n", "feature route");
        repo.git("checkout", "-q", "main");
        conflictingMerge();
        repo.write("i.tsx", "unsaved edit\n");

        service.resolve(repo.root(), "[id].tsx", ConflictSide.OURS);

        assertThat(repo.read("[id].tsx")).isEqualTo("main\n");
        assertThat(repo.read("i.tsx")).isEqualTo("unsaved edit\n");
    }

    @Test
    void duringARebaseOursIsStillTheBranchBeingRebasedNotTheTarget() {
        conflictingRebase();

        service.resolve(repo.root(), "a.txt", ConflictSide.OURS);

        assertThat(repo.read("a.txt")).isEqualTo("feature\n");
    }

    @Test
    void duringARebaseTheirsIsTheIncomingBranch() {
        conflictingRebase();

        service.resolve(repo.root(), "a.txt", ConflictSide.THEIRS);

        assertThat(repo.read("a.txt")).isEqualTo("main\n");
    }

    @Test
    void continueFinishesAMergeWithoutOpeningAnEditor() {
        conflictingMerge();
        repo.write("a.txt", "both\n");
        service.markResolved(repo.root(), "a.txt");

        service.continueOperation(repo.root());

        assertThat(repo.git("rev-list", "--parents", "-1", "HEAD").split(" ")).hasSize(3);
        assertThat(repo.root().resolve(".git/MERGE_HEAD")).doesNotExist();
    }

    @Test
    void continueFinishesARebaseAndAbortRestoresTheBranch() {
        conflictingRebase();
        repo.write("a.txt", "both\n");
        service.markResolved(repo.root(), "a.txt");
        service.continueOperation(repo.root());
        assertThat(repo.git("status", "--porcelain=v2", "--branch")).contains("branch.head feature");
        assertThat(repo.root().resolve(".git/rebase-merge")).doesNotExist();
    }

    @Test
    void abortRestoresThePreMergeState() {
        conflictingMerge();

        service.abortOperation(repo.root());

        assertThat(repo.read("a.txt")).isEqualTo("main\n");
        assertThat(repo.root().resolve(".git/MERGE_HEAD")).doesNotExist();
    }

    @Test
    void skipDropsTheConflictingCommitDuringARebase() {
        conflictingRebase();

        service.skipCommit(repo.root());

        assertThat(repo.read("a.txt")).isEqualTo("main\n");
        assertThat(repo.root().resolve(".git/rebase-merge")).doesNotExist();
    }

    @Test
    void skipOutsideARebaseAndContinueWithoutOperationAreRefused() {
        conflictingMerge();

        assertThatThrownBy(() -> service.skipCommit(repo.root())).isInstanceOf(GitException.class).hasMessageContaining("rebase");
        service.abortOperation(repo.root());
        assertThatThrownBy(() -> service.continueOperation(repo.root())).isInstanceOf(GitException.class);
    }

    @Test
    void takingTheSideThatDeletedTheFileDeletesIt() {
        repo.git("reset", "-q", "--hard", "HEAD~1");
        repo.git("checkout", "-q", "-b", "deleter");
        repo.git("rm", "-q", "a.txt");
        repo.git("commit", "-q", "-m", "delete");
        repo.git("checkout", "-q", "main");
        repo.commit("a.txt", "edited\n", "edit");
        assertThatThrownBy(() -> repo.git("merge", "deleter")).isInstanceOf(IllegalStateException.class);

        service.resolve(repo.root(), "a.txt", ConflictSide.THEIRS);

        assertThat(repo.root().resolve("a.txt")).doesNotExist();
        assertThat(repo.git("status", "--porcelain")).isEqualTo("D  a.txt");
    }

    @Test
    void resolvingAFileThatIsNoLongerConflictedFailsAndKeepsItsContent() {
        conflictingMerge();
        service.markResolved(repo.root(), "a.txt");

        assertThatThrownBy(() -> service.resolve(repo.root(), "a.txt", ConflictSide.OURS))
                .isInstanceOf(GitException.class).hasMessageContaining("no longer conflicted");
    }
}
