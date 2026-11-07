package com.github.krpkbhdn.ccmanager.gitlab;

import com.github.krpkbhdn.ccmanager.github.ForgeCli;
import com.github.krpkbhdn.ccmanager.github.GhAvailability;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Runs the GitLab CLI in a project directory and reports whether it is installed and logged in to a host. */
@Service
@EnableConfigurationProperties(GlabProperties.class)
public class GlabCli extends ForgeCli {

    /** glab reports a rejected or missing token as a failing API call too, so these decide before the network markers do. */
    private static final List<String> SIGN_IN_NEEDED = List.of("401", "403", "unauthorized", "no token", "glab auth login", "not logged in");
    /** An unreachable host says nothing about the login, even when the text mentions it. */
    private static final List<String> UNREACHABLE = List.of("no such host", "dial tcp", "timeout", "connection refused");

    @Autowired
    public GlabCli(GlabProperties properties) {
        this(properties, CommandRunner::run, runningOnWindows(), System::nanoTime);
    }

    GlabCli(GlabProperties properties, Runner runner) {
        this(properties, runner, false, System::nanoTime);
    }

    GlabCli(GlabProperties properties, Runner runner, boolean windows, LongSupplier nanoClock) {
        super(properties.executable(), properties.timeout(), properties.readyTtl(), runner, windows, nanoClock);
    }

    public GhAvailability availability(Path directory, String host) {
        return availability(directory, host, GlabCli::signInNeeded, "auth", "status", "--hostname", host);
    }

    private static boolean signInNeeded(CommandRunner.Result result) {
        var text = outputOf(result);
        return UNREACHABLE.stream().noneMatch(text::contains) && SIGN_IN_NEEDED.stream().anyMatch(text::contains);
    }
}
