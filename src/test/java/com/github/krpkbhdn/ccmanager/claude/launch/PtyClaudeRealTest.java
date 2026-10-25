package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Talks to the real claude binary; the interactive test needs CCM_REAL_CLAUDE=1 and sends no prompt. */
class PtyClaudeRealTest {

    private static final int WAIT_SECONDS = 20;
    private static final long TUI_STARTUP_MILLIS = 6000;
    private static final String ESC = "\u001b";

    @TempDir
    Path directory;

    @Test
    void versionIsPrintedThroughPty() throws Exception {
        Path claude = claudeExecutable();
        PtyProcess process = spawn(claude, "--version");
        String output = readUntilExit(process);

        assertThat(output).containsPattern("\\d+\\.\\d+\\.\\d+");
    }

    @Test
    void interactiveTuiDrawsAndExitsOnSlashExit() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")), "set CCM_REAL_CLAUDE=1");
        PtyProcess process = spawn(claudeExecutable());
        StringBuilder output = new StringBuilder();
        Thread reader = drain(process.getInputStream(), output);

        Thread.sleep(TUI_STARTUP_MILLIS);
        assertThat(output.toString()).contains(ESC + "[");
        process.getOutputStream().write(ESC.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().write(new byte[] {3});
        process.getOutputStream().flush();
        Thread.sleep(500);
        process.getOutputStream().write("/exit\r".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();

        boolean ended = process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS);
        if (!ended) {
            process.destroyForcibly();
        }
        reader.join(TimeUnit.SECONDS.toMillis(2));
        assertThat(ended).as("process ended after /exit").isTrue();
    }

    private PtyProcess spawn(Path claude, String... args) throws IOException {
        String[] command = new String[args.length + 1];
        command[0] = claude.toString();
        System.arraycopy(args, 0, command, 1, args.length);
        return new PtyProcessBuilder(command)
                .setDirectory(directory.toString())
                .setEnvironment(ClaudeCommand.environment(System.getenv(), "ccm-test"))
                .setInitialColumns(PtySpawner.INITIAL_COLUMNS)
                .setInitialRows(PtySpawner.INITIAL_ROWS)
                .start();
    }

    private static String readUntilExit(PtyProcess process) throws Exception {
        StringBuilder output = new StringBuilder();
        Thread reader = drain(process.getInputStream(), output);
        process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS);
        reader.join(TimeUnit.SECONDS.toMillis(2));
        return output.toString();
    }

    private static Thread drain(InputStream stream, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                for (int n = stream.read(buffer); n > 0; n = stream.read(buffer)) {
                    synchronized (sink) {
                        sink.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignored) {
                // pty closed
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Path claudeExecutable() {
        Path candidate = Path.of(System.getProperty("user.home"), ".local", "bin",
                System.getProperty("os.name").startsWith("Windows") ? "claude.exe" : "claude");
        assumeTrue(Files.isExecutable(candidate), "claude not installed at " + candidate);
        return candidate;
    }
}
