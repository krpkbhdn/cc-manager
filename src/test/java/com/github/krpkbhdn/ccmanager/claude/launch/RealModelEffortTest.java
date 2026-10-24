package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real claude, one tiny prompt: the stored selection must reach the session as flags, which the statusLine payload (model and
 * effort) proves. Typing /model into the PTY was tried and could not be confirmed, so the app does not offer it.
 */
class RealModelEffortTest {

    private static final Duration WAIT = Duration.ofSeconds(120);

    @TempDir
    Path dataDir;

    @Test
    void storedSelectionReachesClaude() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var projects = context.getBean(ProjectService.class);
            var project = projects.add(Path.of("").toAbsolutePath());
            projects.selectModel(project.id(), new ModelSelection("haiku", "low"));
            var registry = context.getBean(SessionRegistry.class);
            var usage = context.getBean(SessionUsageService.class);
            var session = context.getBean(ClaudeLauncher.class).launch(project.id(), LaunchMode.NEW);
            await(() -> registry.find(session.ccmSessionId()).map(s -> s.claudeSessionId() != null).orElse(false));

            write(session, "say hi in two words\r");
            await(() -> usage.of(session.ccmSessionId()).isPresent());
            var first = usage.of(session.ccmSessionId()).orElseThrow();
            System.out.println("LAUNCHED_WITH model=" + first.modelName() + " effort=" + first.effort());
            assertThat(first.modelName()).containsIgnoringCase("haiku");
            assertThat(first.effort()).isEqualTo("low");

            write(session, "/exit\r");
            await(() -> registry.find(session.ccmSessionId()).isEmpty());
        }
    }

    private static void write(LaunchedSession session, String text) throws Exception {
        session.process().getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        session.process().getOutputStream().flush();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).as("condition within " + WAIT).isTrue();
    }
}
