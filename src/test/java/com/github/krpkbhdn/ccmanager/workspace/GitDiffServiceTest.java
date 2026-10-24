package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitDiffServiceTest {

    private final GitDiffService service = new GitDiffService();

    @TempDir
    Path repo;

    @BeforeEach
    void requireGit() {
        try {
            assumeTrue(CommandRunner.run(List.of("git", "--version"), null, Duration.ofSeconds(10)).succeeded());
        } catch (IOException e) {
            assumeTrue(false, "git is not installed");
        }
    }

    private void git(String... arguments) throws IOException {
        var command = new java.util.ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(arguments));
        assertThat(CommandRunner.run(command, repo, Duration.ofSeconds(30)).succeeded()).isTrue();
    }

    private FileDiff file(GitDiff diff, String path) {
        return diff.files().stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void directoryWithoutRepositoryIsNotAGitRepository() {
        var diff = service.diff(repo);

        assertThat(diff.gitRepository()).isFalse();
        assertThat(diff.files()).isEmpty();
    }

    @Test
    void combinesStagedUnstagedAndUntrackedChanges() throws IOException {
        git("init", "-q");
        Files.writeString(repo.resolve("tracked.txt"), "one\ntwo\n");
        Files.writeString(repo.resolve("staged.txt"), "s\n");
        git("add", ".");
        git("commit", "-q", "-m", "init");
        Files.writeString(repo.resolve("tracked.txt"), "one\nTWO\n");
        Files.writeString(repo.resolve("staged.txt"), "s2\n");
        git("add", "staged.txt");
        Files.writeString(repo.resolve("new.txt"), "a\nb\n");
        Files.write(repo.resolve("blob.bin"), new byte[] {1, 0, 2});

        var diff = service.diff(repo);

        assertThat(diff.gitRepository()).isTrue();
        assertThat(file(diff, "tracked.txt").change()).isEqualTo(ChangeType.MODIFIED);
        assertThat(file(diff, "tracked.txt").additions()).isEqualTo(1);
        assertThat(file(diff, "staged.txt").deletions()).isEqualTo(1);
        assertThat(file(diff, "new.txt").change()).isEqualTo(ChangeType.UNTRACKED);
        assertThat(file(diff, "new.txt").additions()).isEqualTo(2);
        assertThat(file(diff, "blob.bin").binary()).isTrue();
    }

    @Test
    void repositoryWithoutCommitsDiffsAgainstEmptyTree() throws IOException {
        git("init", "-q");
        Files.writeString(repo.resolve("a.txt"), "x\n");
        git("add", "a.txt");

        var diff = service.diff(repo);

        assertThat(file(diff, "a.txt").change()).isEqualTo(ChangeType.ADDED);
        assertThat(file(diff, "a.txt").additions()).isEqualTo(1);
    }
}
