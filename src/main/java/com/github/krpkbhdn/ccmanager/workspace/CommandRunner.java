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

/** Runs a short-lived command with a hard timeout; stdout and stderr are drained concurrently. */
public final class CommandRunner {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {

        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    private static final Duration READER_GRACE = Duration.ofSeconds(2);
    private static final ExecutorService PIPES = Executors.newVirtualThreadPerTaskExecutor();

    private CommandRunner() {}

    /** @throws IOException when the process cannot be started */
    public static Result run(List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        var builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        var process = builder.start();
        process.getOutputStream().close();
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        var readers = CompletableFuture.allOf(drain(process.getInputStream(), stdout), drain(process.getErrorStream(), stderr));
        try {
            var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                ProcessTree.kill(process);
            }
            awaitReaders(process, readers);
            return new Result(finished ? process.exitValue() : -1, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8), !finished);
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

    public static CompletableFuture<Void> drain(InputStream stream, ByteArrayOutputStream into) {
        return CompletableFuture.runAsync(() -> {
            try (stream) {
                var chunk = new byte[8192];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    into.write(chunk, 0, count);
                }
            } catch (IOException e) {
                // closed after the grace period; the buffer holds what arrived
            }
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
