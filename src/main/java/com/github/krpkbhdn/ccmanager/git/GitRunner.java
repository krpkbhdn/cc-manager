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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The single place that spawns git (the user's own CLI, so credential helpers, hooks, signing and config just work).
 * Non-interactive by design: prompts are disabled, so a missing credential fails fast with {@link Kind#AUTH}.
 */
@Component
public class GitRunner {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final Duration SYNC_TIMEOUT = Duration.ofMinutes(10);

    private static final ExecutorService PIPES = Executors.newVirtualThreadPerTaskExecutor();
    private static final List<String> BASE_COMMAND =
            List.of("git", "-c", "core.quotepath=false", "-c", "color.ui=never", "-c", "core.fsmonitor=false");

    private final Map<String, String> extraEnvironment;

    @Autowired
    public GitRunner() {
        this(Map.of());
    }

    /** Extra environment for every spawned git, e.g. GIT_CONFIG_GLOBAL to isolate tests from the user's config. */
    public GitRunner(Map<String, String> extraEnvironment) {
        this.extraEnvironment = Map.copyOf(extraEnvironment);
    }

    public static boolean isAvailable() {
        try {
            var process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record Result(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** stdout of a command that must succeed. @throws GitException otherwise */
    public String out(Path repo, String... args) {
        return run(repo, DEFAULT_TIMEOUT, null, null, List.of(args)).stdout();
    }

    /** Like {@link #out} with {@code stdin} fed to git (patches, commit messages, path lists). */
    public String outWithInput(Path repo, byte[] stdin, String... args) {
        return run(repo, DEFAULT_TIMEOUT, null, stdin, List.of(args)).stdout();
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
            var kind = GitFailure.classify(output);
            throw new GitException(kind, GitFailure.message(kind, output), output);
        }
        return result;
    }

    private Result execute(Path repo, Duration timeout, Consumer<String> progress, byte[] stdin, List<String> args) {
        var command = new ArrayList<>(BASE_COMMAND);
        command.addAll(args);
        var builder = new ProcessBuilder(command).directory(repo.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        builder.environment().put("LC_ALL", "C");
        builder.environment().put("GIT_EDITOR", "true");
        builder.environment().putIfAbsent("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
        builder.environment().putAll(extraEnvironment);
        try {
            var process = builder.start();
            var stdout = async(() -> new String(readAll(process), StandardCharsets.UTF_8));
            var stderr = async(() -> readLines(process, progress));
            var stdinWritten = async(() -> writeAndClose(process, stdin));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new GitException(Kind.TIMEOUT, "git " + GitFailure.subcommand(args) + " timed out", "");
            }
            stdinWritten.join();
            return new Result(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException e) {
            throw new GitException(Kind.GIT_MISSING, "Git is not installed or not on PATH", String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException(Kind.OTHER, "Interrupted", "");
        }
    }

    private static <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, PIPES);
    }

    private static Void writeAndClose(Process process, byte[] stdin) {
        try (var in = process.getOutputStream()) {
            if (stdin != null) {
                in.write(stdin);
            }
        } catch (IOException e) {
            // git may exit before reading everything; its exit code tells the story
        }
        return null;
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
