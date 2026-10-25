package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The single place that spawns git (the user's own CLI, so credential helpers, hooks, signing and config just work).
 * Non-interactive by design: prompts are disabled, so a missing credential fails fast with {@link Kind#AUTH}.
 */
@Component
@EnableConfigurationProperties(GitProperties.class)
public class GitRunner {


    private static final ExecutorService PIPES = Executors.newVirtualThreadPerTaskExecutor();
    private static final List<String> BASE_COMMAND =
            List.of("git", "-c", "core.quotepath=false", "-c", "color.ui=never", "-c", "core.fsmonitor=false");

    private final GitProperties properties;
    private final Map<String, String> extraEnvironment;

    @Autowired
    public GitRunner(GitProperties properties) {
        this(properties, Map.of());
    }

    public GitRunner() {
        this(GitProperties.defaults());
    }

    /** Extra environment for every spawned git, e.g. GIT_CONFIG_GLOBAL to isolate tests from the user's config. */
    public GitRunner(GitProperties properties, Map<String, String> extraEnvironment) {
        this.properties = properties;
        this.extraEnvironment = Map.copyOf(extraEnvironment);
    }

    /** The git tunables, shared with the services built on this runner. */
    public GitProperties properties() {
        return properties;
    }


    public record Result(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** stdout of a command that must succeed. @throws GitException otherwise */
    public String out(Path repo, String... args) {
        return run(repo, properties.commandTimeout(), null, null, List.of(args)).stdout();
    }

    /** Like {@link #out} with {@code stdin} fed to git (patches, commit messages, path lists). */
    public String outWithInput(Path repo, byte[] stdin, String... args) {
        return run(repo, properties.commandTimeout(), null, stdin, List.of(args)).stdout();
    }

    /** Result of a command whose non-zero exit is an answer, not an error (e.g. merge-base --is-ancestor). */
    public Result tryRun(Path repo, String... args) {
        return execute(repo, properties.commandTimeout(), null, null, List.of(args));
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
        if (!Files.isDirectory(repo)) {
            throw new GitException(Kind.NOT_A_REPOSITORY, "Folder does not exist: " + repo, "");
        }
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
            var stdout = new ByteArrayOutputStream();
            var stderr = new StringBuffer();
            var readers = CompletableFuture.allOf(CommandRunner.drain(process.getInputStream(), stdout), async(() -> readLines(process, progress, stderr)));
            var stdinWritten = async(() -> writeAndClose(process, stdin));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new GitException(Kind.TIMEOUT, "git " + GitFailure.subcommand(args) + " timed out", "");
            }
            stdinWritten.join();
            CommandRunner.awaitReaders(process, readers);
            return new Result(process.exitValue(), stdout.toString(StandardCharsets.UTF_8), stderr.toString());
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

    private static Void readLines(Process process, Consumer<String> progress, StringBuffer text) {
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
            return null;
        }
        return null;
    }

    private static void emit(StringBuilder line, Consumer<String> progress) {
        if (progress != null && !line.isEmpty()) {
            progress.accept(line.toString());
        }
        line.setLength(0);
    }
}
