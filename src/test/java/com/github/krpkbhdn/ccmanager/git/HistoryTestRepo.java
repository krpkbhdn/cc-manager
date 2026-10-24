package com.github.krpkbhdn.ccmanager.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A throw-away repository driven by the real git CLI, with a repo-local identity and no signing. */
final class HistoryTestRepo {

    private final Path root;

    private HistoryTestRepo(Path root) {
        this.root = root;
    }

    static boolean gitAvailable() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    static HistoryTestRepo init(Path root) {
        var repo = new HistoryTestRepo(root);
        repo.git("init", "-q", "-b", "main");
        repo.git("config", "user.name", "Test User");
        repo.git("config", "user.email", "test@example.com");
        repo.git("config", "commit.gpgsign", "false");
        repo.git("config", "tag.gpgsign", "false");
        repo.git("config", "core.autocrlf", "false");
        return repo;
    }

    Path root() {
        return root;
    }

    String git(String... args) {
        var command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(30, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
            }
            return output.strip();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    void write(String file, String content) {
        try {
            var path = root.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String read(String file) {
        try {
            return Files.readString(root.resolve(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String commit(String file, String content, String message) {
        write(file, content);
        git("add", "--", file);
        git("commit", "-q", "-m", message);
        return git("rev-parse", "HEAD");
    }
}
