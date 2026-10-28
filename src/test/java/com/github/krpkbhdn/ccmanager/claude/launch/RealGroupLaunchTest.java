package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Launches the real claude in a group of two scratch repos; sends no prompt, so no tokens are used. */
class RealGroupLaunchTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @TempDir
    Path dataDir;

    @TempDir
    Path repos;

    @Test
    void groupSessionRunsInGroupDirectoryWithAddDirPerMember() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var projects = context.getBean(ProjectService.class);
            var one = projects.add(Files.createDirectory(repos.resolve("repo-one")));
            var two = projects.add(Files.createDirectory(repos.resolve("repo-two")));
            var group = context.getBean(ProjectGroupService.class).create("scratch-group", List.of(one.id(), two.id()));
            var registry = context.getBean(SessionRegistry.class);

            var session = context.getBean(ClaudeLauncher.class).launch(group.id(), LaunchMode.NEW);
            Thread.sleep(10_000);
            var out = new String(session.process().getInputStream().readNBytes(session.process().getInputStream().available()), StandardCharsets.UTF_8);
            System.out.println("PTY_OUT=" + out.replaceAll("\u001B\\[[0-9;?]*[A-Za-z]", "").replaceAll("\\s+", " "));

            var wmi = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*repo-one*' -and $_.Name -like 'claude*' } | ForEach-Object { $_.CommandLine }")
                    .redirectErrorStream(true).start();
            var commandLine = new String(wmi.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("GROUP_DIR=" + group.path());
            System.out.println("COMMAND_LINE=" + commandLine);
            System.out.println("CLAUDE_MD=" + Files.readString(group.path().resolve("CLAUDE.md")).replace("\n", " | "));
            assertThat(group.path()).startsWith(dataDir.resolve("groups"));
            assertThat(commandLine).contains("--add-dir").contains(one.path().toString()).contains(two.path().toString());
            assertThat(Files.readString(group.path().resolve("CLAUDE.md"))).contains(one.path().toString(), two.path().toString());

            session.process().destroyForcibly();
            await(() -> registry.find(session.ccmSessionId()).isEmpty());
            assertThat(one.path()).isDirectory();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(condition.getAsBoolean()).as("condition within " + WAIT).isTrue();
    }
}
