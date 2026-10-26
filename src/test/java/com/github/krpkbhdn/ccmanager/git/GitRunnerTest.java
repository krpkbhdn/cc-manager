package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRunnerTest {

    @TempDir
    Path root;

    @Test
    void aMissingWorkingDirectoryIsNotReportedAsMissingGit() {
        var sandbox = GitCliSandbox.create(root);

        assertThatThrownBy(() -> sandbox.runner.out(root.resolve("deleted-project"), "status"))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_A_REPOSITORY));
    }

    @Test
    void failureInNonRepositoryIsClassified() {
        var sandbox = GitCliSandbox.create(root);

        assertThatThrownBy(() -> sandbox.runner.out(root, "status"))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_A_REPOSITORY));
    }

    @Test
    void largeStdinAndLargeStdoutDoNotDeadlock() {
        var sandbox = GitCliSandbox.create(root);
        var big = "x".repeat(5_000_000).getBytes(StandardCharsets.UTF_8);

        var hash = sandbox.runner.outWithInput(sandbox.repo, big, "hash-object", "--stdin", "-w");
        var content = sandbox.runner.out(sandbox.repo, "cat-file", "-p", hash.strip());

        assertThat(content).hasSize(big.length);
    }

    @Test
    void timeoutKillsTheProcessAndReportsKind() {
        var sandbox = GitCliSandbox.create(root);
        var started = System.nanoTime();

        assertThatThrownBy(() -> sandbox.runner.run(sandbox.repo, Duration.ofMillis(300), null, null, List.of("-c", "alias.wait=!sleep 30", "wait")))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void writingCommandsGetTheSyncTimeout() {
        var runner = new GitRunner();
        var properties = runner.properties();

        assertThat(runner.timeoutFor(List.of("-c", "core.editor=true", "rebase", "--continue"))).isEqualTo(properties.syncTimeout());
        assertThat(runner.timeoutFor(List.of("checkout", "x"))).isEqualTo(properties.syncTimeout());
        assertThat(runner.timeoutFor(List.of("status"))).isEqualTo(properties.commandTimeout());
    }

    @Test
    void batchSshIsOnlyForNetworkCommandsWithoutUserSshSettings() {
        assertThat(GitRunner.mayForceBatchSsh(List.of("fetch"), Map.of())).isTrue();
        assertThat(GitRunner.mayForceBatchSsh(List.of("status"), Map.of())).isFalse();
        assertThat(GitRunner.mayForceBatchSsh(List.of("push"), Map.of("GIT_SSH", "plink"))).isFalse();
        assertThat(GitRunner.mayForceBatchSsh(List.of("push"), Map.of("GIT_SSH_COMMAND", "ssh -i k"))).isFalse();
    }

    @Test
    void aConfiguredCoreSshCommandIsNotOverridden() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.git("remote", "add", "origin", "ssh://example.invalid/x");
        sandbox.git("config", "core.sshCommand", "echo custom-ssh >&2; false");

        assertThatThrownBy(() -> sandbox.runner.out(sandbox.repo, "fetch", "origin"))
                .isInstanceOfSatisfying(GitException.class, e -> assertThat(e.output()).contains("custom-ssh"));
    }

    @Test
    void passesNonAsciiPathsAndQuotesArguments() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("dir with space/файл.txt", "a\n");

        sandbox.runner.out(sandbox.repo, "add", "--", "dir with space/файл.txt");

        assertThat(sandbox.git("ls-files")).contains("dir with space/файл.txt");
    }

    @Test
    void streamsStderrProgressLines() {
        var sandbox = GitCliSandbox.createWithOrigin(root);
        var lines = new ArrayList<String>();

        var target = root.resolve("copy").toString();

        sandbox.runner.run(root, sandbox.runner.properties().commandTimeout(), lines::add, null, List.of("clone", "--progress", root.resolve("origin.git").toString(), target));

        assertThat(lines).anyMatch(line -> line.startsWith("Cloning into"));
    }

    @Test
    void repositoryDiffContextOfZeroDoesNotShrinkHunks() {
        var sandbox = GitCliSandbox.create(root);
        sandbox.write("a.txt", "1\n2\n3\n4\n5\n6\n7\n");
        sandbox.commitAll("c1");
        sandbox.git("config", "diff.context", "0");
        sandbox.write("a.txt", "1\n2\n3\nX\n5\n6\n7\n");

        assertThat(sandbox.runner.out(sandbox.repo, "diff")).contains(" 3\n", " 5\n");
    }
}
