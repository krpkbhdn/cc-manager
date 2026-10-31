package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRunnerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Test
    void outputUnderTheLimitIsIntactAndExitCodeIsReported() throws IOException {
        var result = CommandRunner.run(shell("echo hello & exit 3"), null, TIMEOUT);

        assertThat(result.stdout().strip()).isEqualTo("hello");
        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.timedOut()).isFalse();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void outputOverTheLimitIsTruncated() throws IOException {
        var result = CommandRunner.run(shell("echo 0123456789012345678901234567890123456789"), null, TIMEOUT, 10);

        assertThat(result.stdout()).isEqualTo("0123456789");
        assertThat(result.exitCode()).isZero();
        assertThat(result.truncated()).isTrue();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void aCommandOverTheTimeoutIsKilledAndReported() throws IOException {
        var result = CommandRunner.run(shell("ping -n 30 127.0.0.1 > nul || sleep 30"), null, Duration.ofMillis(500));

        assertThat(result.timedOut()).isTrue();
        assertThat(result.succeeded()).isFalse();
    }

    private static List<String> shell(String line) {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? List.of("cmd", "/c", line)
                : List.of("sh", "-c", line.replace(" & ", "; ").replace("ping -n 30 127.0.0.1 > nul || ", ""));
    }
}
