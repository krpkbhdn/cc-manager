package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.PayloadApplicationEvent;

/**
 * Real claude through the whole stack with one tiny prompt: the statusLine command must reach the server. Rate limits are only
 * in the payload for Pro/Max subscriptions, so they are printed, not asserted.
 */
class RealRateLimitTest {

    private static final Duration WAIT = Duration.ofSeconds(120);

    @TempDir
    Path dataDir;

    @Test
    void statusLineCommandPostsAfterTheFirstReply() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var project = context.getBean(ProjectService.class).add(Path.of("").toAbsolutePath());
            var registry = context.getBean(SessionRegistry.class);
            var limits = context.getBean(RateLimitService.class);
            var reports = new CopyOnWriteArrayList<StatusLineReport>();
            context.addApplicationListener(event -> {
                if (event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof StatusLineReport report) {
                    reports.add(report);
                }
            });
            var session = context.getBean(ClaudeLauncher.class).launch(project.id(), LaunchMode.NEW);
            await(() -> registry.find(session.ccmSessionId()).map(s -> s.claudeSessionId() != null).orElse(false));

            write(session, "say hi in two words\r");
            await(() -> !reports.isEmpty());

            System.out.println("STATUSLINE_KEYS=" + reports.getLast().payload().propertyNames());
            System.out.println("RATE_LIMITS=" + limits.current());
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
