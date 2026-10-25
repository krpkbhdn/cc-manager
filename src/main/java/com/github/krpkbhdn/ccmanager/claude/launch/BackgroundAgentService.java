package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackgroundAgentService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundAgentService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ClaudeInstallationService installation;

    public BackgroundAgentService(ClaudeInstallationService installation) {
        this.installation = installation;
    }

    /** Empty when Claude is missing or reports none; never throws for a CLI failure. */
    public List<BackgroundAgent> list(Path projectDirectory) {
        try {
            ClaudeInstallation current = installation.current();
            if (!current.installed() || current.executable() == null) {
                return List.of();
            }
            return BackgroundAgentParser.parse(run(current.executable(), projectDirectory));
        } catch (IOException | RuntimeException e) {
            log.debug("Listing background agents failed", e);
            return List.of();
        }
    }

    private String run(Path executable, Path directory) throws IOException {
        var result = CommandRunner.run(List.of(executable.toString(), "agents", "--json", "--cwd", WindowsArgument.directoryArgument(executable.toString(), directory)), null, TIMEOUT);
        if (result.timedOut()) {
            log.debug("claude agents timed out");
            return "";
        }
        return result.stdout();
    }
}
