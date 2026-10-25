package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWatcherTest {

    private static final long WAIT_MILLIS = 5000;
    private static final long QUIET_MILLIS = 800;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

    private static final GitProperties PROPERTIES = watcherProperties();
    private final GitWatcher watcher = new GitWatcher(new GitRunner(PROPERTIES));
    private HistoryTestRepo repo;

    private static GitProperties watcherProperties() {
        var defaults = GitProperties.defaults();
        return new GitProperties(defaults.commandTimeout(), defaults.syncTimeout(), Duration.ofMillis(60), 50,
                defaults.maxDiffLines(), defaults.maxUntrackedBytes());
    }

    @BeforeEach
    void setUp(@TempDir Path directory) {
        assumeTrue(HistoryTestRepo.gitAvailable());
        repo = HistoryTestRepo.init(directory);
        repo.commit("a.txt", "1", "first");
    }

    @AfterEach
    void tearDown() {
        watcher.close();
    }

    private static boolean eventually(BooleanSupplier condition) {
        var deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(25);
        }
        return condition.getAsBoolean();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void editingAWorkingTreeFileNotifies() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);

        repo.write("a.txt", "edited");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void aCommitNotifiesThroughRefsAndHead() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);

        repo.git("commit", "-q", "--allow-empty", "-m", "empty");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void aNewBranchInANestedRefsDirectoryNotifies() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);

        repo.git("branch", "feature/deep/er");
        calls.set(0);
        sleep(QUIET_MILLIS);
        repo.git("branch", "feature/deep/other");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void aLinkedWorktreeSeesRefChangesOfTheCommonGitDirectory(@TempDir Path elsewhere) {
        var worktree = elsewhere.resolve("linked");
        repo.git("worktree", "add", "-q", "-b", "linked", worktree.toString());
        var calls = new AtomicInteger();
        watcher.subscribe(worktree, calls::incrementAndGet);
        sleep(200);

        repo.git("branch", "created-in-the-main-checkout");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void filesInNewlyCreatedSubdirectoriesAreSeen() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        repo.write("src/deep/one.txt", "1");
        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
        sleep(QUIET_MILLIS);
        calls.set(0);

        repo.write("src/deep/two.txt", "2");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void aBurstOfWritesIsCoalesced() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);

        for (var i = 0; i < 20; i++) {
            repo.write("burst" + i + ".txt", "x");
        }

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
        sleep(QUIET_MILLIS);
        assertThat(calls.get()).isLessThan(10);
    }

    @Test
    void ignoredDirectoriesStaySilent() {
        repo.write("node_modules/pkg/index.js", "1");
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);

        repo.write("node_modules/pkg/index.js", "2");
        repo.write("build/out.class", "2");

        sleep(QUIET_MILLIS);
        assertThat(calls.get()).isZero();
    }

    @Test
    void unsubscribeStopsNotificationsAndIsIdempotent() {
        var calls = new AtomicInteger();
        var unsubscribe = watcher.subscribe(repo.root(), calls::incrementAndGet);
        unsubscribe.run();
        unsubscribe.run();

        repo.write("a.txt", "edited");

        sleep(QUIET_MILLIS);
        assertThat(calls.get()).isZero();
    }

    @Test
    void aFailingListenerDoesNotSilenceOthersAndOneUnsubscribeKeepsTheRepositoryWatched() {
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), () -> {
            throw new IllegalStateException("boom");
        });
        var second = watcher.subscribe(repo.root(), calls::incrementAndGet);
        watcher.subscribe(repo.root(), () -> {}).run();

        repo.write("a.txt", "edited");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
        second.run();
    }

    @Test
    void resubscribingAfterTheLastSubscriberLeftWorksAgain() {
        watcher.subscribe(repo.root(), () -> {}).run();

        editingAWorkingTreeFileNotifies();
    }

    @Test
    void aDirectoryThatIsNotARepositoryIsStillWatchedAsATree(@TempDir Path plain) throws IOException {
        var calls = new AtomicInteger();
        watcher.subscribe(plain, calls::incrementAndGet);

        Files.writeString(plain.resolve("x.txt"), "1");

        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }

    @Test
    void onWindowsAFolderCanBeMovedWhileWatchedAndADeepChangeStillNotifies() throws IOException {
        assumeTrue(WINDOWS);
        repo.write("src/main/deep/a.txt", "1");
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);

        Files.move(repo.root().resolve("src/main"), repo.root().resolve("src/moved"));
        assertThat(repo.root().resolve("src/moved/deep/a.txt")).exists();
        var afterMove = calls.get();
        repo.write("src/moved/deep/a.txt", "2");

        assertThat(eventually(() -> calls.get() > afterMove)).isTrue();
    }

    @Test
    void onWindowsARefTwoNewFoldersDeepInsideTheGitDirectoryStillNotifies() throws IOException {
        assumeTrue(WINDOWS);
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);
        var deep = repo.root().resolve(".git/refs/heads/user/x");
        Files.createDirectories(deep);
        sleep(QUIET_MILLIS);
        var before = calls.get();

        Files.writeString(deep.resolve("y"), "0000000000000000000000000000000000000000\n");

        assertThat(eventually(() -> calls.get() > before)).isTrue();
    }

    @Test
    void onWindowsChangesInsideIgnoredFoldersAtAnyDepthDoNotNotify() throws IOException {
        assumeTrue(WINDOWS);
        repo.write("module/build/out.txt", "0");
        var calls = new AtomicInteger();
        watcher.subscribe(repo.root(), calls::incrementAndGet);
        sleep(200);
        var before = calls.get();

        repo.write("module/build/out.txt", "1");
        sleep(QUIET_MILLIS);

        assertThat(calls.get()).isEqualTo(before);
    }

    @Test
    void hugeTreesFallBackToWatchingOnlyTheRoot(@TempDir Path huge) throws IOException {
        assumeFalse(WINDOWS, "Windows watches the whole tree with one recursive handle");
        for (var i = 0; i <= PROPERTIES.maxWatchedDirectories(); i++) {
            Files.createDirectories(huge.resolve("d" + i));
        }
        var calls = new AtomicInteger();
        watcher.subscribe(huge, calls::incrementAndGet);
        sleep(200);

        Files.writeString(huge.resolve("d5").resolve("nested.txt"), "1");
        sleep(QUIET_MILLIS);
        assertThat(calls.get()).isZero();

        Files.writeString(huge.resolve("top.txt"), "1");
        assertThat(eventually(() -> calls.get() >= 1)).isTrue();
    }
}
