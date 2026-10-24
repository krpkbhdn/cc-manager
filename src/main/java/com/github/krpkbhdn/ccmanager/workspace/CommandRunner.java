package com.github.krpkbhdn.ccmanager.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Runs a short-lived command with a hard timeout; stdout and stderr are drained concurrently. */
public final class CommandRunner {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {

        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    private CommandRunner() {}

    /** @throws IOException when the process cannot be started */
    public static Result run(List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        var builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        var process = builder.start();
        process.getOutputStream().close();
        var stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        var stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            var finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                killTree(process);
            }
            return new Result(finished ? process.exitValue() : -1, stdout.join(), stderr.join(), !finished);
        } catch (InterruptedException e) {
            killTree(process);
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

    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String read(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
