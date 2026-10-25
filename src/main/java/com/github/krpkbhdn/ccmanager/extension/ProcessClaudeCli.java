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
        var current = installation.current();
        if (!current.installed() || current.executable() == null) {
            throw new ExtensionException(current.problem() == null ? "Claude Code is not installed" : current.problem());
        }
        var command = new ArrayList<String>();
        command.add(current.executable().toString());
        arguments.forEach(argument -> command.add(host.windows() ? WindowsArgument.quote(argument) : argument));
        try {
            var result = CommandRunner.run(command, directory, timeout);
            if (result.timedOut()) {
                throw new ExtensionException("claude " + String.join(" ", arguments.subList(0, Math.min(2, arguments.size()))) + " timed out after " + timeout.toSeconds() + " s");
            }
            return result;
        } catch (IOException e) {
            throw new ExtensionException("Could not run claude: " + e.getMessage());
        }
    }
}
