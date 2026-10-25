package com.github.krpkbhdn.ccmanager.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;

/** A throw-away repository driven by the real git CLI, isolated from the user's global and system git config. */
final class GitCliSandbox {

    static final String MAIN = "main";

    final Path root;
    final Path repo;
    final GitRunner runner;

    private GitCliSandbox(Path root, Path repo, GitRunner runner) {
        this.root = root;
        this.repo = repo;
        this.runner = runner;
    }

    static GitCliSandbox create(Path root) {
        return create(root, GitProperties.defaults());
    }

    static GitCliSandbox create(Path root, GitProperties properties) {
        Assumptions.assumeTrue(HistoryTestRepo.gitAvailable(), "git is not installed");
        var runner = isolatedRunner(root, properties);
        var sandbox = new GitCliSandbox(root, root.resolve("work"), runner);
        sandbox.initialise(sandbox.repo, false);
        return sandbox;
    }

    /** Same sandbox plus a bare repository as {@code origin}; MAIN is pushed there once it has a commit. */
    static GitCliSandbox createWithOrigin(Path root) {
        var sandbox = create(root);
        sandbox.write("README.md", "hello\n");
        sandbox.commitAll("initial");
        var origin = root.resolve("origin.git");
        sandbox.runIn(root, "init", "--bare", "-b", MAIN, origin.toString());
        sandbox.git("remote", "add", "origin", origin.toString());
        sandbox.git("push", "-u", "origin", MAIN);
        return sandbox;
    }

    /** A second working copy of origin, for creating diverging history. */
    GitCliSandbox cloneOrigin(String directoryName) {
        var clone = new GitCliSandbox(root, root.resolve(directoryName), runner);
        runIn(root, "clone", root.resolve("origin.git").toString(), clone.repo.toString());
        clone.configureIdentity(clone.repo);
        return clone;
    }

    String git(String... args) {
        return runner.out(repo, args);
    }

    String runIn(Path directory, String... args) {
        return runner.out(directory, args);
    }

    void write(String relativePath, String content) {
        writeBytes(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    void writeBytes(String relativePath, byte[] content) {
        try {
            var file = repo.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String read(String relativePath) {
        try {
            return Files.readString(repo.resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String commitAll(String message) {
        git("add", "-A");
        git("commit", "-m", message);
        return git("rev-parse", "HEAD").strip();
    }

    private void initialise(Path directory, boolean existing) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!existing) {
            runner.out(directory, "init", "-b", MAIN);
        }
        configureIdentity(directory);
    }

    private void configureIdentity(Path directory) {
        runner.out(directory, "config", "user.name", "Test User");
        runner.out(directory, "config", "user.email", "test@example.com");
        runner.out(directory, "config", "commit.gpgsign", "false");
        runner.out(directory, "config", "core.autocrlf", "false");
    }

    private static GitRunner isolatedRunner(Path root, GitProperties properties) {
        try {
            var emptyConfig = Files.createFile(root.resolve("empty.gitconfig")).toString();
            return new GitRunner(properties, Map.of("GIT_CONFIG_GLOBAL", emptyConfig, "GIT_CONFIG_SYSTEM", emptyConfig,
                    "GIT_CONFIG_NOSYSTEM", "1"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
