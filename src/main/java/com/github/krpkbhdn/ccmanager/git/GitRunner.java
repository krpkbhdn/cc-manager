package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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


    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitRunner.class);
    private static final Set<String> NETWORK = Set.of("fetch", "pull", "push", "ls-remote", "clone");
    private static final Set<String> WRITES = Set.of("checkout", "switch", "restore", "reset", "merge", "rebase", "cherry-pick", "revert",
            "stash", "commit", "add", "rm", "apply", "am", "clean", "pull", "worktree");
    private static final Set<String> HOOK_RUNNING = Set.of("checkout", "switch", "merge", "rebase", "cherry-pick", "revert", "commit", "am");
    private static final ExecutorService PIPES = Executors.newVirtualThreadPerTaskExecutor();
    private static final List<String> BASE_COMMAND =
            List.of("git", "-c", "core.quotepath=false", "-c", "color.ui=never", "-c", "core.fsmonitor=false",
                    "-c", "diff.noprefix=false", "-c", "diff.mnemonicPrefix=false", "-c", "log.showSignature=false",
                    "-c", "diff.context=3");

    /** Pins the a/ b/ prefixes the diff parser reads, over git 2.45+ diff.srcPrefix/dstPrefix. */
    public static final String SRC_PREFIX = "--src-prefix=a/";
    public static final String DST_PREFIX = "--dst-prefix=b/";

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


    public record Result(int exitCode, String stdout, String stderr, boolean truncated) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** stdout of a command that must succeed. @throws GitException otherwise */
    public String out(Path repo, String... args) {
        return run(repo, timeoutFor(List.of(args)), null, null, List.of(args)).stdout();
    }

    /** Like {@link #out} with {@code stdin} fed to git (patches, commit messages, path lists). */
    public String outWithInput(Path repo, byte[] stdin, String... args) {
        return run(repo, timeoutFor(List.of(args)), null, stdin, List.of(args)).stdout();
    }

    /** Result of a command whose non-zero exit is an answer, not an error (e.g. merge-base --is-ancestor). */
    public Result tryRun(Path repo, String... args) {
        return execute(repo, timeoutFor(List.of(args)), null, null, List.of(args));
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
        if (result.truncated()) {
            throw new GitException(Kind.OTHER, "git " + GitFailure.subcommand(args) + " output is too large (over " + CommandRunner.MAX_OUTPUT_BYTES + " bytes)", "");
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
        builder.environment().putAll(extraEnvironment);
        if (mayForceBatchSsh(args, builder.environment()) && !hasConfiguredSsh(repo)) {
            builder.environment().put("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GitException(Kind.GIT_MISSING, "Git is not installed or not on PATH", String.valueOf(e.getMessage()));
        }
        try {
            return collect(process, timeout, progress, stdin, args);
        } catch (InterruptedException e) {
            if (runsHooks(args)) {
                finishWrite(process, timeout);
            } else {
                ProcessTree.kill(process);
            }
            Thread.currentThread().interrupt();
            throw new GitException(Kind.OTHER, "Interrupted", "");
        }
    }

    /** A hook-running command is not killed on interrupt (index.lock, half-switched tree): it runs to its end, so "Interrupted" never hides a running commit. */
    private static void finishWrite(Process process, Duration timeout) {
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                ProcessTree.kill(process);
            }
        } catch (InterruptedException again) {
            ProcessTree.kill(process);
        }
    }

    private Result collect(Process process, Duration timeout, Consumer<String> progress, byte[] stdin, List<String> args) throws InterruptedException {
        var stdout = new ByteArrayOutputStream();
        var stderr = new StringBuffer();
        var stdoutTruncated = CommandRunner.drain(process.getInputStream(), stdout);
        var readers = CompletableFuture.allOf(stdoutTruncated, async(() -> readLines(process.getErrorStream(), progress, stderr, CommandRunner.MAX_OUTPUT_BYTES)));
        var stdinWritten = async(() -> writeAndClose(process, stdin));
        if (runsHooks(args)) {
            process.waitFor();
        } else if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            ProcessTree.kill(process);
            throw new GitException(Kind.TIMEOUT, "git " + GitFailure.subcommand(args) + " timed out", "");
        }
        stdinWritten.join();
        CommandRunner.awaitReaders(process, readers);
        return new Result(process.exitValue(), stdout.toString(StandardCharsets.UTF_8), stderr.toString(), stdoutTruncated.getNow(false));
    }

    /** Commands that run user hooks are never killed at the timeout (index.lock, half-switched tree); network and read commands are. */
    static boolean runsHooks(List<String> args) {
        return HOOK_RUNNING.contains(GitFailure.subcommand(args));
    }

    /** Writing commands get the long timeout. */
    Duration timeoutFor(List<String> args) {
        return WRITES.contains(GitFailure.subcommand(args)) ? properties.syncTimeout() : properties.commandTimeout();
    }

    /** BatchMode only for network commands, and never over the user's own GIT_SSH or GIT_SSH_COMMAND. */
    static boolean mayForceBatchSsh(List<String> args, Map<String, String> environment) {
        return NETWORK.contains(GitFailure.subcommand(args)) && !environment.containsKey("GIT_SSH") && !environment.containsKey("GIT_SSH_COMMAND");
    }

    private boolean hasConfiguredSsh(Path repo) {
        return execute(repo, properties.commandTimeout(), null, null, List.of("config", "--get", "core.sshCommand")).ok();
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

    /** Progress still flows for every line, but {@code text} keeps at most {@code maxChars}, like the capped stdout. */
    static Void readLines(InputStream stream, Consumer<String> progress, StringBuffer text, int maxChars) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var line = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    emit(line, progress);
                } else if (line.length() < maxChars) {
                    line.append((char) c);
                }
                if (text.length() < maxChars) {
                    text.append((char) c);
                }
            }
            emit(line, progress);
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static void emit(StringBuilder line, Consumer<String> progress) {
        if (progress != null && !line.isEmpty()) {
            try {
                progress.accept(line.toString());
            } catch (RuntimeException e) {
                // keep reading: git would die writing to a full stderr pipe; only the class is logged, the message may hold paths or tokens
                log.warn("git progress listener failed: {}", e.getClass().getName());
            }
        }
        line.setLength(0);
    }
}
