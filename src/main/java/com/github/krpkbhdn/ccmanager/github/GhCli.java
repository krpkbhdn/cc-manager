package com.github.krpkbhdn.ccmanager.github;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.WindowsArgument;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Runs the GitHub CLI in a project directory and reports whether it is installed and logged in. */
@Service
@EnableConfigurationProperties(GhProperties.class)
public class GhCli {

    @FunctionalInterface
    interface Runner {
        CommandRunner.Result run(List<String> command, Path workingDirectory, Duration timeout) throws IOException;
    }

    private final GhProperties properties;
    private final Runner runner;
    private final boolean windows;
    private final LongSupplier nanoClock;
    private volatile long readyUntilNanos;

    @Autowired
    public GhCli(GhProperties properties) {
        this(properties, CommandRunner::run, System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), System::nanoTime);
    }

    GhCli(GhProperties properties, Runner runner) {
        this(properties, runner, false, System::nanoTime);
    }

    GhCli(GhProperties properties, Runner runner, boolean windows, LongSupplier nanoClock) {
        this.properties = properties;
        this.runner = runner;
        this.windows = windows;
        this.nanoClock = nanoClock;
    }

    /** READY is remembered for {@link GhProperties#readyTtl()} so the poll does not run {@code gh auth status} every tick. */
    public GhAvailability availability(Path directory) {
        var now = nanoClock.getAsLong();
        if (readyUntilNanos != 0 && now - readyUntilNanos < 0) {
            return GhAvailability.READY;
        }
        try {
            var result = run(directory, "auth", "status");
            if (result.succeeded()) {
                readyUntilNanos = now + properties.readyTtl().toNanos();
                return GhAvailability.READY;
            }
            readyUntilNanos = 0;
            return result.timedOut() || !signInNeeded(result) ? GhAvailability.UNKNOWN : GhAvailability.NOT_AUTHENTICATED;
        } catch (IOException e) {
            return GhAvailability.MISSING;
        }
    }

    private static boolean signInNeeded(CommandRunner.Result result) {
        var text = (result.stdout() + " " + result.stderr()).toLowerCase(Locale.ROOT);
        return text.contains("gh auth login") || text.contains("not logged in");
    }

    /** Arguments are quoted for CreateProcess on Windows, where the JDK does not escape embedded quotes. @throws IOException when gh cannot be started */
    public CommandRunner.Result run(Path directory, String... arguments) throws IOException {
        var command = new ArrayList<String>();
        command.add(properties.executable());
        for (var argument : arguments) {
            command.add(windows ? WindowsArgument.quote(argument) : argument);
        }
        return runner.run(command, directory, properties.timeout());
    }
}
