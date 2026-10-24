package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The single place that spawns git (the user's own CLI, so credential helpers, hooks, signing and config just work).
 * Non-interactive by design: prompts are disabled, so a missing credential fails fast with {@link Kind#AUTH}.
 */
@Component
public class GitRunner {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final Duration SYNC_TIMEOUT = Duration.ofMinutes(10);

    public record Result(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** stdout of a command that must succeed. @throws GitException otherwise */
    public String out(Path repo, String... args) {
        return run(repo, DEFAULT_TIMEOUT, null, null, List.of(args)).stdout();
    }

    /** Result of a command whose non-zero exit is an answer, not an error (e.g. merge-base --is-ancestor). */
    public Result tryRun(Path repo, String... args) {
        return execute(repo, DEFAULT_TIMEOUT, null, null, List.of(args));
    }

    /**
     * Runs git; stderr lines (git's progress output) go to {@code progress} as they arrive.
     *
     * @param stdin bytes for stdin or null
     * @throws GitException with a classified kind when the exit code is non-zero
     */
    public Result run(Path repo, Duration timeout, Consumer<String> progress, byte[] stdin, List<String> args) {
        var result = execute(repo, timeout, progress, stdin, args);
        if (!result.ok()) {
            var output = (result.stderr() + "\n" + result.stdout()).strip();
            throw new GitException(GitFailure.classify(output), GitFailure.headline(output), output);
        }
        return result;
    }

    private Result execute(Path repo, Duration timeout, Consumer<String> progress, byte[] stdin, List<String> args) {
        var command = new ArrayList<>(List.of("git", "-c", "core.quotepath=false", "-c", "color.ui=never", "-c", "core.fsmonitor=false"));
        command.addAll(args);
        var builder = new ProcessBuilder(command).directory(repo.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        builder.environment().put("LC_ALL", "C");
        builder.environment().putIfAbsent("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
        try {
            var process = builder.start();
            try (var in = process.getOutputStream()) {
                if (stdin != null) {
                    in.write(stdin);
                }
            }
            var stdout = CompletableFuture.supplyAsync(() -> new String(readAll(process), StandardCharsets.UTF_8));
            var stderr = CompletableFuture.supplyAsync(() -> readLines(process, progress));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new GitException(Kind.TIMEOUT, "git " + args.getFirst() + " timed out", "");
            }
            return new Result(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException e) {
            throw new GitException(Kind.GIT_MISSING, "Git is not installed or not on PATH", String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException(Kind.OTHER, "Interrupted", "");
        }
    }

    private static byte[] readAll(Process process) {
        try {
            return process.getInputStream().readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static String readLines(Process process, Consumer<String> progress) {
        var text = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            var line = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    emit(line, progress);
                } else {
                    line.append((char) c);
                }
                text.append((char) c);
            }
            emit(line, progress);
        } catch (IOException e) {
            return text.toString();
        }
        return text.toString();
    }

    private static void emit(StringBuilder line, Consumer<String> progress) {
        if (progress != null && !line.isEmpty()) {
            progress.accept(line.toString());
        }
        line.setLength(0);
    }
}
