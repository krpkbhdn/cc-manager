package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real run of the installed claude against HookServer. Costs one haiku call; enabled only with env CCM_SMOKE=true
 * (CCM_SMOKE=true ./gradlew test --tests "*ClaudeHookSmokeTest" -i).
 */
class ClaudeHookSmokeTest {

    private static final int CLAUDE_TIMEOUT_SECONDS = 120;
    private static final String CCM_ID = "smoke-1";
    private static final String DEFAULT_PROMPT = "Reply with the single word ok";
    private static final String PROJECT_HOOK_MARKER = "project-hook-fired.txt";

    @TempDir
    Path workDir;

    @Test
    void hooksFromSettingsFileArriveAndProjectHooksStillFire() throws Exception {
        assumeTrue("true".equals(System.getenv("CCM_SMOKE")), "set CCM_SMOKE=true to run the real claude smoke test");
        assumeTrue(claudeVersion() != null, "claude is not on PATH");
        var mapper = JsonMapper.builder().build();
        var events = new CopyOnWriteArrayList<HookEvent>();
        var server = new HookServer(mock(HookEventStore.class), event -> events.add((HookEvent) event), mapper,
                new HookProperties(100, 1_048_576, 500, java.time.Duration.ofSeconds(1)),
                mock(com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint.class),
                mock(com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService.class));
        server.start();
        try {
            var settings = new HookSettingsWriter(server, new AppProperties(workDir.resolve("data")), mapper).write(CCM_ID).settings();
            writeProjectHook();

            var output = runClaude(settings);

            report(events, output);
            assertThat(events).extracting(HookEvent::type)
                    .contains(HookEventType.USER_PROMPT_SUBMIT, HookEventType.STOP, HookEventType.SESSION_END);
            assertThat(workDir.resolve(PROJECT_HOOK_MARKER)).exists();
        } finally {
            server.stop();
        }
    }

    private void writeProjectHook() throws IOException {
        var claudeDir = Files.createDirectories(workDir.resolve(".claude"));
        Files.writeString(claudeDir.resolve("settings.json"), """
                {"hooks":{"UserPromptSubmit":[{"hooks":[{"type":"command","command":"echo fired > %s"}]}]}}
                """.formatted(PROJECT_HOOK_MARKER));
    }

    private String runClaude(Path settings) throws Exception {
        var process = new ProcessBuilder("claude", "-p", prompt(), "--model", "haiku",
                "--settings", settings.toString())
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        var output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(CLAUDE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return output;
    }

    private static String prompt() {
        return System.getenv().getOrDefault("CCM_SMOKE_PROMPT", DEFAULT_PROMPT);
    }

    private static String claudeVersion() {
        try {
            var process = new ProcessBuilder("claude", "--version").redirectErrorStream(true).start();
            var version = new String(process.getInputStream().readAllBytes()).strip();
            return process.waitFor() == 0 ? version : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private void report(List<HookEvent> events, String output) throws IOException {
        var lines = events.stream().map(event -> event.rawName() + " " + event.payload()).toList();
        Files.write(workDir.resolveSibling("ccm-smoke-report.txt"), lines);
        System.out.println("SMOKE claude output: " + output.strip());
        lines.forEach(line -> System.out.println("SMOKE event: " + line));
    }
}
