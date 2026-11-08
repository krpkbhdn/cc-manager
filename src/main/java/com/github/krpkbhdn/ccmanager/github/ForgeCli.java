package com.github.krpkbhdn.ccmanager.github;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.WindowsArgument;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** What the gh and glab wrappers share: running the executable with Windows quoting and a cached "logged in" check per key. */
public abstract class ForgeCli {

    @FunctionalInterface
    public interface Runner {
        CommandRunner.Result run(List<String> command, Path workingDirectory, Duration timeout) throws IOException;
    }

    private final String executable;
    private final Duration timeout;
    private final Duration readyTtl;
    private final Runner runner;
    private final boolean windows;
    private final LongSupplier nanoClock;
    private final Map<String, Long> readyUntilNanosByKey = new ConcurrentHashMap<>();

    protected ForgeCli(String executable, Duration timeout, Duration readyTtl, Runner runner, boolean windows, LongSupplier nanoClock) {
        this.executable = executable;
        this.timeout = timeout;
        this.readyTtl = readyTtl;
        this.runner = runner;
        this.windows = windows;
        this.nanoClock = nanoClock;
    }

    protected static boolean runningOnWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** READY is remembered per [key] for the ready TTL so the poll does not run {@code auth status} every tick. */
    protected GhAvailability availability(Path directory, String key, Predicate<CommandRunner.Result> signInNeeded, String... authStatusArguments) {
        var now = nanoClock.getAsLong();
        var readyUntil = readyUntilNanosByKey.get(key);
        if (readyUntil != null && now - readyUntil < 0) {
            return GhAvailability.READY;
        }
        try {
            var result = run(directory, authStatusArguments);
            if (result.succeeded()) {
                readyUntilNanosByKey.put(key, now + readyTtl.toNanos());
                return GhAvailability.READY;
            }
            readyUntilNanosByKey.remove(key);
            return result.timedOut() || !signInNeeded.test(result) ? GhAvailability.UNKNOWN : GhAvailability.NOT_AUTHENTICATED;
        } catch (IOException e) {
            return GhAvailability.MISSING;
        }
    }

    protected static String outputOf(CommandRunner.Result result) {
        return (result.stdout() + " " + result.stderr()).toLowerCase(Locale.ROOT);
    }

    /** Arguments are quoted for CreateProcess on Windows, where the JDK does not escape embedded quotes. @throws IOException when the CLI cannot be started */
    public CommandRunner.Result run(Path directory, String... arguments) throws IOException {
        var command = new ArrayList<String>();
        command.add(executable);
        for (var argument : arguments) {
            command.add(windows ? WindowsArgument.quote(argument) : argument);
        }
        return runner.run(command, directory, timeout);
    }
}
