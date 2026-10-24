package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackgroundAgentService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundAgentService.class);
    private static final long TIMEOUT_SECONDS = 10;

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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private String run(Path executable, Path directory) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(executable.toString(), "agents", "--json", "--cwd", directory.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process));
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            log.debug("claude agents timed out");
            return "";
        }
        return output.join();
    }

    private static String readAll(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
