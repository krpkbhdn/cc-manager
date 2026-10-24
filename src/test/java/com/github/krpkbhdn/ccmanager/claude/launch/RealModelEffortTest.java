package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventStore;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real claude, two tiny prompts: the project's selection reaches a launched session as flags, and restarting with --resume
 * keeps the conversation while switching model and effort, leaving the user's saved Claude settings untouched. Typing
 * /model or /effort into the PTY does switch a session (submit sent as a separate write), but claude saves it as the user's default.
 */
class RealModelEffortTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final Path USER_SETTINGS = Path.of(System.getProperty("user.home"), ".claude", "settings.json");
    private static final int SCREEN_TAIL = 1500;

    @TempDir
    Path dataDir;

    private final StringBuffer screen = new StringBuffer();

    @Test
    void selectionReachesClaudeAndRestartSwitchesItKeepingTheConversation() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        var settingsBefore = Files.exists(USER_SETTINGS) ? Files.readString(USER_SETTINGS) : "";
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var projects = context.getBean(ProjectService.class);
            var project = projects.add(Path.of("").toAbsolutePath());
            projects.selectModel(project.id(), new ModelSelection("haiku", "low"));
            var registry = context.getBean(SessionRegistry.class);
            var usage = context.getBean(SessionUsageService.class);
            var hooks = context.getBean(HookEventStore.class);
            var launcher = context.getBean(ClaudeLauncher.class);

            var first = launcher.launch(project.id(), LaunchMode.NEW);
            watch(first);
            await(() -> registry.find(first.ccmSessionId()).map(s -> s.claudeSessionId() != null).orElse(false), "first session registered");
            Thread.sleep(3_000);
            submit(first, "Remember the word pineapple. Reply with just ok.");
            await(() -> usage.of(first.ccmSessionId()).isPresent(), "first statusLine");
            var launched = usage.of(first.ccmSessionId()).orElseThrow();
            System.out.println("LAUNCHED_WITH model=" + launched.modelName() + " effort=" + launched.effort());
            assertThat(launched.modelName()).containsIgnoringCase("haiku");
            assertThat(launched.effort()).isEqualTo("low");

            var conversation = registry.find(first.ccmSessionId()).orElseThrow().claudeSessionId();
            screen.setLength(0);
            submit(first, "/exit");
            await(() -> registry.find(first.ccmSessionId()).isEmpty(), "first session exited");

            screen.setLength(0);
            var second = launcher.restart(project.id(), conversation, new ModelSelection("sonnet", "high"));
            watch(second);
            await(() -> registry.find(second.ccmSessionId()).map(s -> s.claudeSessionId() != null).orElse(false), "resumed session registered");
            Thread.sleep(3_000);
            submit(second, "Which word did I ask you to remember? Reply with that word only.");
            await(() -> usage.of(second.ccmSessionId()).isPresent(), "second statusLine");
            var restarted = usage.of(second.ccmSessionId()).orElseThrow();
            System.out.println("RESTARTED_WITH model=" + restarted.modelName() + " effort=" + restarted.effort());
            await(() -> hooks.recent(second.ccmSessionId(), 20).stream().anyMatch(e -> e.type().name().equals("STOP")), "answer");
            var stops = hooks.recent(second.ccmSessionId(), 20).stream().filter(e -> e.type().name().equals("STOP")).toList();
            System.out.println("RESTARTED_ANSWER=" + stops);
            assertThat(restarted.modelName()).containsIgnoringCase("sonnet");
            assertThat(restarted.effort()).isEqualTo("high");
            assertThat(stops.toString().toLowerCase()).contains("pineapple");
            kill(second);
        }
        assertThat(Files.exists(USER_SETTINGS) ? Files.readString(USER_SETTINGS) : "").isEqualTo(settingsBefore);
    }

    private void watch(LaunchedSession session) {
        var in = session.process().getInputStream();
        var reader = new Thread(() -> {
            try {
                var buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    screen.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (java.io.IOException ignored) {
                // process ended
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private String screenTail() {
        var text = screen.toString().replaceAll("\\u001B\\[[0-9;?]*[ -/]*[@-~]", "").replaceAll("[\\r\\n]+", " | ");
        return text.substring(Math.max(0, text.length() - SCREEN_TAIL));
    }

    private static void kill(LaunchedSession session) throws Exception {
        new ProcessBuilder("taskkill", "/PID", String.valueOf(session.process().pid()), "/T", "/F").start().waitFor();
    }

    private static void submit(LaunchedSession session, String text) throws Exception {
        write(session, text);
        Thread.sleep(700);
        write(session, "\r");
    }

    private static void write(LaunchedSession session, String text) throws Exception {
        session.process().getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        session.process().getOutputStream().flush();
    }

    private void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).as(what + " within " + WAIT + ", screen: " + screenTail()).isTrue();
    }
}
