package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitStashServiceTest {

    private final GitStashService service = new GitStashService(new GitRunner());
    private HistoryTestRepo repo;

    @BeforeEach
    void setUp(@TempDir Path directory) {
        assumeTrue(HistoryTestRepo.gitAvailable());
        repo = HistoryTestRepo.init(directory);
        repo.commit("a.txt", "base\n", "base");
    }

    @Test
    void pushedEntriesAreListedNewestFirstWithMessageAndBranch() {
        repo.write("a.txt", "one\n");
        service.push(repo.root(), "first work", false);
        repo.write("a.txt", "two\n");
        service.push(repo.root(), null, false);

        var stashes = service.list(repo.root());

        assertThat(stashes).hasSize(2);
        assertThat(stashes.get(0).index()).isZero();
        assertThat(stashes.get(0).message()).contains("base");
        assertThat(stashes.get(0).branch()).isEqualTo("main");
        assertThat(stashes.get(1).message()).isEqualTo("first work");
        assertThat(stashes.get(1).date()).isNotNull();
    }

    @Test
    void pushWithoutChangesFailsAsNothingToStash() {
        assertThatThrownBy(() -> service.push(repo.root(), "x", false))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOTHING_TO_COMMIT));
    }

    @Test
    void untrackedFilesAreStashedOnlyOnRequestAndShownAsUntracked() {
        repo.write("a.txt", "changed\n");
        repo.write("new.txt", "fresh\n");
        service.push(repo.root(), "with untracked", true);

        var diff = service.show(repo.root(), hashOf(0));

        assertThat(repo.root().resolve("new.txt")).doesNotExist();
        assertThat(diff.files()).extracting(file -> file.path() + ":" + file.change())
                .containsExactlyInAnyOrder("a.txt:MODIFIED", "new.txt:" + ChangeType.UNTRACKED);
    }

    @Test
    void showFollowsTheEntryHashEvenWhenItsPositionChanges() {
        repo.write("a.txt", "one\n");
        service.push(repo.root(), "one", false);
        var oneHash = hashOf(0);
        repo.write("b.txt", "other\n");
        repo.git("add", "b.txt");
        service.push(repo.root(), "two", false);

        var diff = service.show(repo.root(), oneHash);

        assertThat(diff.files()).extracting(file -> file.path()).containsExactly("a.txt");
    }

    @Test
    void popRestoresChangesAndRemovesTheEntryWhileApplyKeepsIt() {
        repo.write("a.txt", "work\n");
        service.push(repo.root(), "w", false);

        service.apply(repo.root(), 0, hashOf(0));
        assertThat(repo.read("a.txt")).isEqualTo("work\n");
        assertThat(service.list(repo.root())).hasSize(1);

        repo.git("checkout", "--", "a.txt");
        service.pop(repo.root(), 0, hashOf(0));
        assertThat(service.list(repo.root())).isEmpty();
    }

    @Test
    void dropRemovesTheChosenEntry() {
        repo.write("a.txt", "one\n");
        service.push(repo.root(), "keep me", false);
        repo.write("a.txt", "two\n");
        service.push(repo.root(), "drop me", false);

        service.drop(repo.root(), 0, hashOf(0));

        assertThat(service.list(repo.root())).extracting(stash -> stash.message()).containsExactly("keep me");
    }

    @Test
    void conflictingPopThrowsConflictAndKeepsTheEntry() {
        repo.write("a.txt", "stashed\n");
        service.push(repo.root(), "w", false);
        repo.commit("a.txt", "committed\n", "diverge");

        assertThatThrownBy(() -> service.pop(repo.root(), 0, hashOf(0)))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
        assertThat(service.list(repo.root())).hasSize(1);
    }

    @Test
    void actionsOnAShiftedListAreRefusedAndTouchNothing() {
        repo.write("a.txt", "mine\n");
        service.push(repo.root(), "mine", false);
        var shownHash = hashOf(0);
        repo.write("a.txt", "claude\n");
        service.push(repo.root(), "claude", false);

        assertThatThrownBy(() -> service.drop(repo.root(), 0, shownHash))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.getMessage()).contains("stash list changed"));
        assertThatThrownBy(() -> service.pop(repo.root(), 0, shownHash)).isInstanceOf(GitException.class);
        assertThat(service.list(repo.root())).extracting(stash -> stash.message()).containsExactly("claude", "mine");
    }

    private String hashOf(int index) {
        return service.list(repo.root()).get(index).hash();
    }
}
