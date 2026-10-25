package com.github.krpkbhdn.ccmanager.claude.install;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Finds the native claude binary (PATH, then the native-installer location) and reads version and login state. */
@Service
public class ClaudeInstallationService {

    static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(ClaudeInstallationService.class);
    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+");
    private static final int EXIT_LOGGED_OUT = 1;
    private static final String WINDOWS_INSTALL = "irm https://claude.ai/install.ps1 | iex";
    private static final String UNIX_INSTALL = "curl -fsSL https://claude.ai/install.sh | bash";

    private final HostEnvironment host;
    private final ClaudeBinaryLocator locator;
    private volatile ClaudeInstallation current;

    @Autowired
    public ClaudeInstallationService(HostEnvironment host, ClaudeBinaryLocator locator) {
        this.host = host;
        this.locator = locator;
    }

    /** Cached result of the last detection (detected on first use). */
    public ClaudeInstallation current() {
        var cached = current;
        return cached != null ? cached : recheck();
    }

    /** Runs detection again (UI check-again button). */
    public synchronized ClaudeInstallation recheck() {
        current = detect();
        return current;
    }

    static Optional<String> parseVersion(String output) {
        var matcher = VERSION.matcher(output == null ? "" : output.strip());
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    static String notFoundProblem(boolean windows) {
        return "Claude Code was not found. Install it with: " + (windows ? WINDOWS_INSTALL : UNIX_INSTALL);
    }

    private ClaudeInstallation detect() {
        var executable = locator.find(host);
        if (executable.isEmpty()) {
            return new ClaudeInstallation(false, null, null, LoginState.UNKNOWN, notFoundProblem(host.windows()));
        }
        var path = executable.get();
        var version = readVersion(path);
        if (version.isEmpty()) {
            var problem = "Found " + path + " but it did not report a version. Reinstall Claude Code: "
                    + (host.windows() ? WINDOWS_INSTALL : UNIX_INSTALL);
            return new ClaudeInstallation(false, path, null, LoginState.UNKNOWN, problem);
        }
        return new ClaudeInstallation(true, path, version.get(), readLoginState(path), null);
    }

    private Optional<String> readVersion(Path executable) {
        return execute(executable, "--version")
                .filter(CommandRunner.Result::succeeded)
                .flatMap(result -> parseVersion(result.stdout()));
    }

    private LoginState readLoginState(Path executable) {
        return execute(executable, "auth", "status")
                .filter(result -> !result.timedOut())
                .map(result -> switch (result.exitCode()) {
                    case 0 -> LoginState.LOGGED_IN;
                    case EXIT_LOGGED_OUT -> LoginState.LOGGED_OUT;
                    default -> LoginState.UNKNOWN;
                })
                .orElse(LoginState.UNKNOWN);
    }

    private Optional<CommandRunner.Result> execute(Path executable, String... arguments) {
        var command = new java.util.ArrayList<>(List.of(executable.toString()));
        command.addAll(List.of(arguments));
        try {
            return Optional.of(CommandRunner.run(command, null, COMMAND_TIMEOUT));
        } catch (IOException e) {
            log.warn("Could not run {} {}: {}", executable, String.join(" ", arguments), e.getMessage());
            return Optional.empty();
        }
    }
}
