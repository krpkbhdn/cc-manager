package com.github.krpkbhdn.ccmanager.github;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Runs the GitHub CLI in a project directory and reports whether it is installed and logged in. */
@Service
@EnableConfigurationProperties(GhProperties.class)
public class GhCli extends ForgeCli {

    @Autowired
    public GhCli(GhProperties properties) {
        this(properties, CommandRunner::run, runningOnWindows(), System::nanoTime);
    }

    GhCli(GhProperties properties, Runner runner) {
        this(properties, runner, false, System::nanoTime);
    }

    GhCli(GhProperties properties, Runner runner, boolean windows, LongSupplier nanoClock) {
        super(properties.executable(), properties.timeout(), properties.readyTtl(), runner, windows, nanoClock);
    }

    public GhAvailability availability(Path directory) {
        return availability(directory, "", GhCli::signInNeeded, "auth", "status");
    }

    private static boolean signInNeeded(CommandRunner.Result result) {
        var text = outputOf(result);
        return text.contains("gh auth login") || text.contains("not logged in");
    }
}
