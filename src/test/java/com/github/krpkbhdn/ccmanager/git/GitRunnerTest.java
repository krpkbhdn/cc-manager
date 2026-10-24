package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRunnerTest {

    @TempDir
    Path root;

    @Test
    void reportsGitAsAvailable() {
        assumeTrue(GitRunner.isAvailable());

        assertThat(GitRunner.isAvailable()).isTrue();
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

        sandbox.runner.run(root, GitRunner.DEFAULT_TIMEOUT, lines::add, null, List.of("clone", "--progress", root.resolve("origin.git").toString(), target));

        assertThat(lines).anyMatch(line -> line.startsWith("Cloning into"));
    }
}
