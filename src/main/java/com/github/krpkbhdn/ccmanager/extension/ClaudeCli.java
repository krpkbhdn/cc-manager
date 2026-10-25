package com.github.krpkbhdn.ccmanager.extension;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Runs the claude binary with the given arguments; a seam so services can be tested without a real claude. */
@FunctionalInterface
public interface ClaudeCli {

    int DESCRIBED_ARGUMENTS = 2;

    /**
     * @param directory working directory, null for none
     * @return the finished command, whatever its exit code
     * @throws ExtensionException when claude is missing, cannot start or times out
     */
    CommandRunner.Result execute(Path directory, Duration timeout, List<String> arguments);

    /**
     * Like {@link #execute} but attached to a pseudo terminal, for commands that need one. {@code onOutput} receives the
     * output so far as it grows. Interrupting the thread kills the process. The default runs without a terminal.
     */
    default CommandRunner.Result executeInTerminal(Path directory, Duration timeout, List<String> arguments, Consumer<String> onOutput) {
        return execute(directory, timeout, arguments);
    }

    /** @return stdout of a command that exited with 0 @throws ExtensionException otherwise, carrying stderr */
    default String executeChecked(Path directory, Duration timeout, List<String> arguments) {
        var result = execute(directory, timeout, arguments);
        if (!result.succeeded()) {
            throw failure(arguments, result);
        }
        return result.stdout();
    }

    static ExtensionException failure(List<String> arguments, CommandRunner.Result result) {
        var output = result.stderr().isBlank() ? result.stdout() : result.stderr();
        var detail = output.isBlank() ? "exit code " + result.exitCode() : output.strip();
        return new ExtensionException("claude " + String.join(" ", arguments.subList(0, Math.min(DESCRIBED_ARGUMENTS, arguments.size())))
                + " failed: " + detail, output);
    }
}
