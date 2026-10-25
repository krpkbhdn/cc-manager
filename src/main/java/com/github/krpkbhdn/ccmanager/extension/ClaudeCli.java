package com.github.krpkbhdn.ccmanager.extension;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
