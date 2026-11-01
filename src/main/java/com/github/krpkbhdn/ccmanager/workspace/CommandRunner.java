package com.github.krpkbhdn.ccmanager.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs a short-lived command with a hard timeout; stdout and stderr are drained concurrently, each kept up to {@link #MAX_OUTPUT_BYTES}. */
public final class CommandRunner {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean truncated) {

        public Result(int exitCode, String stdout, String stderr, boolean timedOut) {
            this(exitCode, stdout, stderr, timedOut, false);
        }

        public boolean succeeded() {
            return !timedOut && !truncated && exitCode == 0;
        }
    }

    public static final int MAX_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final Duration READER_GRACE = Duration.ofSeconds(2);
    private static final ExecutorService PIPES = Executors.newVirtualThreadPerTaskExecutor();

    private CommandRunner() {}

    /** @throws IOException when the process cannot be started */
    public static Result run(List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        return run(command, workingDirectory, timeout, MAX_OUTPUT_BYTES);
    }

    /** @param maxOutputBytes per stream; the rest is read and dropped so the process never blocks on a full pipe */
    static Result run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputBytes) throws IOException {
        var builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        var process = builder.start();
        process.getOutputStream().close();
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        var stdoutTruncated = drain(process.getInputStream(), stdout, maxOutputBytes);
        var stderrTruncated = drain(process.getErrorStream(), stderr, maxOutputBytes);
        try {
            var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                ProcessTree.kill(process);
            }
            awaitReaders(process, CompletableFuture.allOf(stdoutTruncated, stderrTruncated));
            return new Result(finished ? process.exitValue() : -1, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8), !finished,
                    stdoutTruncated.getNow(false) || stderrTruncated.getNow(false));
        } catch (InterruptedException e) {
            ProcessTree.kill(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command.getFirst(), e);
        }
    }

    /** Starts a process without waiting for it; its output is discarded. */
    public static void startDetached(List<String> command) throws IOException {
        var process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
    }

    /**
     * Gives the readers a short grace after the process ended: a grandchild (a git hook running {@code indexer &}) may keep the
     * pipes open for good, so then the streams are closed off-thread and whatever was read so far is used.
     */
    public static void awaitReaders(Process process, CompletableFuture<?> readers) {
        try {
            readers.get(READER_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            PIPES.execute(() -> closeQuietly(process.getInputStream(), process.getErrorStream()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            // a reader only fails on a closed pipe; its partial output is still in the buffer
        }
    }

    /** Completes with true when the stream held more than the cap and the rest was dropped. */
    public static CompletableFuture<Boolean> drain(InputStream stream, ByteArrayOutputStream into) {
        return drain(stream, into, MAX_OUTPUT_BYTES);
    }

    private static CompletableFuture<Boolean> drain(InputStream stream, ByteArrayOutputStream into, int maxBytes) {
        return CompletableFuture.supplyAsync(() -> {
            var truncated = false;
            try (stream) {
                var chunk = new byte[8192];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    var kept = Math.min(count, Math.max(0, maxBytes - into.size()));
                    into.write(chunk, 0, kept);
                    truncated |= kept < count;
                }
            } catch (IOException e) {
                // closed after the grace period; the buffer holds what arrived
            }
            return truncated;
        }, PIPES);
    }

    private static void closeQuietly(InputStream... streams) {
        for (var stream : streams) {
            try {
                stream.close();
            } catch (IOException e) {
                // nothing left to release
            }
        }
    }
}
