package com.github.krpkbhdn.ccmanager.extension;

import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.launch.WindowsArgument;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ExtensionProperties.class)
class ProcessClaudeCli implements ClaudeCli {

    private final ClaudeInstallationService installation;
    private final HostEnvironment host;

    ProcessClaudeCli(ClaudeInstallationService installation, HostEnvironment host) {
        this.installation = installation;
        this.host = host;
    }

    @Override
    public CommandRunner.Result execute(Path directory, Duration timeout, List<String> arguments) {
        return run(timeout, arguments, command -> CommandRunner.run(command, directory, timeout));
    }

    @Override
    public CommandRunner.Result executeInTerminal(Path directory, Duration timeout, List<String> arguments, Consumer<String> onOutput) {
        return run(timeout, arguments, command -> PtyCommandRunner.run(command, directory, timeout, onOutput));
    }

    private interface Launch {
        CommandRunner.Result start(List<String> command) throws IOException;
    }

    private CommandRunner.Result run(Duration timeout, List<String> arguments, Launch launch) {
        var current = installation.current();
        if (!current.installed() || current.executable() == null) {
            throw new ExtensionException(current.problem() == null ? "Claude Code is not installed" : current.problem());
        }
        var command = new ArrayList<String>();
        command.add(current.executable().toString());
        if (host.windows()) {
            WindowsArgument.requireSafeForBatchFile(Stream.concat(command.stream(), arguments.stream()).toList());
        }
        arguments.forEach(argument -> command.add(host.windows() ? WindowsArgument.quote(argument) : argument));
        try {
            var result = launch.start(command);
            if (result.timedOut()) {
                throw new ExtensionException("claude " + String.join(" ", arguments.subList(0, Math.min(DESCRIBED_ARGUMENTS, arguments.size()))) + " timed out after " + timeout.toSeconds() + " s");
            }
            return result;
        } catch (IOException e) {
            throw new ExtensionException("Could not run claude: " + e.getMessage());
        }
    }
}
