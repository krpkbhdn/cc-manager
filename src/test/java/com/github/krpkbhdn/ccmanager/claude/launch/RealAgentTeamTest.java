package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Real claude, one tiny task: the seeded team's lead delegates to the tester subagent, which runs on haiku. Claude may
 * touch ~/.claude/settings.json, so the test restores it byte for byte.
 */
class RealAgentTeamTest {

    private static final Duration WAIT = Duration.ofSeconds(100);
    private static final Path USER_SETTINGS = Path.of(System.getProperty("user.home"), ".claude", "settings.json");
    private static final String TASK = "Ask the tester role to read the first line of settings.gradle.kts with the Read tool and tell me what it found. "
            + "Use no other tools yourself and change no files.";

    @TempDir
    Path dataDir;

    @Test
    void leadDelegatesToTheTesterRole() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        byte[] settingsBefore = Files.exists(USER_SETTINGS) ? Files.readAllBytes(USER_SETTINGS) : null;
        try {
            runTeamTask();
        } finally {
            if (settingsBefore != null) {
                Files.write(USER_SETTINGS, settingsBefore);
            }
        }
        if (settingsBefore != null) {
            assertThat(Files.readAllBytes(USER_SETTINGS)).isEqualTo(settingsBefore);
        }
    }

    private void runTeamTask() throws Exception {
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var project = context.getBean(ProjectService.class).add(Path.of("").toAbsolutePath());
            var team = context.getBean(TeamService.class).list().getFirst();
            var registry = context.getBean(SessionRegistry.class);
            var session = context.getBean(ClaudeLauncher.class).launchTeam(project.id(), team, TASK);
            var screen = drain(session);
            String id = session.ccmSessionId();
            try {
                await(screen, () -> subagents(registry, id).stream().anyMatch(a -> "tester".equals(a.agentType()) && !a.running()));
                await(screen, () -> registry.find(id).map(s -> s.status() == AgentStatus.IDLE).orElse(false));
                var jdbc = context.getBean(JdbcClient.class);
                var usage = context.getBean(com.github.krpkbhdn.ccmanager.usage.SessionUsageService.class).of(id);
                System.out.println("LEAD_USAGE=" + usage);
                assertThat(usage.get().modelName()).containsIgnoringCase("opus");
                assertThat(usage.get().effort()).isEqualTo("high");
                var subagentEffort = jdbc.sql("SELECT event || ':' || substr(payload, instr(payload, '\"effort\"'), 24) FROM hook_event WHERE ccm_session_id = ? AND agent_id IS NOT NULL AND agent_id <> '' AND payload LIKE '%\"effort\"%'").param(id).query(String.class).list();
                System.out.println("SUBAGENT_EFFORT=" + subagentEffort);
                assertThat(subagentEffort).isNotEmpty().allMatch(entry -> entry.contains("low"));
                System.out.println("SUBAGENTS=" + subagents(registry, id));
                await(screen, () -> subagentModels(jdbc, id).toLowerCase().contains("haiku"));
                System.out.println("TRANSCRIPT_MODELS=" + subagentModels(jdbc, id));
            } finally {
                stop(session);
            }
        }
    }

    private static java.util.List<SubagentSnapshot> subagents(SessionRegistry registry, String id) {
        return registry.find(id).map(s -> s.subagents()).orElse(java.util.List.of());
    }

    /** The subagent transcript the SubagentStop hook points at holds the model each of its replies came from. */
    private static String subagentModels(JdbcClient jdbc, String id) {
        var payload = jdbc.sql("SELECT payload FROM hook_event WHERE ccm_session_id = ? AND event = 'SubagentStop' ORDER BY id DESC LIMIT 1")
                .param(id).query(String.class).optional();
        if (payload.isEmpty()) {
            return "";
        }
        var matcher = java.util.regex.Pattern.compile("\"agent_transcript_path\":\"([^\"]+)\"").matcher(payload.get());
        if (!matcher.find()) {
            return "";
        }
        Path transcript = Path.of(matcher.group(1).replace("\\\\", "\\"));
        var models = new java.util.TreeSet<String>();
        var model = java.util.regex.Pattern.compile("\"modelId\":\"([^\"]+)\"").matcher(read(transcript));
        while (model.find()) {
            models.add(model.group(1));
        }
        return models.toString();
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void stop(LaunchedSession session) throws Exception {
        session.process().getOutputStream().write("/exit".getBytes(StandardCharsets.UTF_8));
        session.process().getOutputStream().flush();
        Thread.sleep(700);
        session.process().getOutputStream().write("\r".getBytes(StandardCharsets.UTF_8));
        session.process().getOutputStream().flush();
        Thread.sleep(3000);
        session.process().destroy();
    }

    /** An unread PTY stalls claude; the collected text is printed when a wait fails. */
    private static StringBuffer drain(LaunchedSession session) {
        var screen = new StringBuffer();
        var input = session.process().getInputStream();
        var reader = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    screen.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // the process ended
            }
        });
        reader.setDaemon(true);
        reader.start();
        return screen;
    }

    private static void await(StringBuffer screen, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(300);
        }
        if (!condition.getAsBoolean()) {
            System.out.println("SCREEN_TAIL=" + screen.substring(Math.max(0, screen.length() - 3000)));
        }
        assertThat(condition.getAsBoolean()).as("condition within " + WAIT).isTrue();
    }
}
