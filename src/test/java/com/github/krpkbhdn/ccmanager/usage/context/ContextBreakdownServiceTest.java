package com.github.krpkbhdn.ccmanager.usage.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ClaudeCli;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContextBreakdownServiceTest {

    private static final Path DIRECTORY = Path.of("work");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration FAILURE_TTL = Duration.ofSeconds(10);
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String OUTPUT = """
            ## Context Usage

            **Model:** m
            **Tokens:** 10 / 100 (10%)

            ### Estimated usage by category
            | Category | Tokens | Percentage |
            |----------|--------|------------|
            | Messages | 10 | 10.0% |
            """;

    private final ClaudeCli claude = mock(ClaudeCli.class);
    private final MutableClock clock = new MutableClock();
    private final ContextBreakdownService service = new ContextBreakdownService(claude, new ContextBreakdownProperties(TIMEOUT, TTL, FAILURE_TTL), clock);

    @BeforeEach
    void succeedByDefault() {
        when(claude.execute(any(), any(), anyList())).thenReturn(new CommandRunner.Result(0, OUTPUT, "", false));
    }

    @Test
    void resumesAForkWithoutPersistenceWhenAnIdIsGiven() {
        var result = service.measure(DIRECTORY, ID, false);

        var arguments = ArgumentCaptor.forClass(List.class);
        verify(claude).execute(eq(DIRECTORY), eq(TIMEOUT), arguments.capture());
        assertThat(arguments.getValue()).containsExactly("-p", "--resume", ID, "--fork-session", "--no-session-persistence", "/context");
        assertThat(result.breakdown().approximateMessages()).isFalse();
        verify(claude, times(1)).execute(any(), any(), anyList());
    }

    @Test
    void runsAFreshEstimateWithoutAnId() {
        var result = service.measure(DIRECTORY, null, false);

        assertThat(ContextBreakdownService.arguments(null)).containsExactly("-p", "/context");
        assertThat(result.breakdown().approximateMessages()).isTrue();
    }

    @Test
    void neverRetriesWhenNoBreakdownIsPrinted() {
        when(claude.execute(any(), any(), anyList())).thenReturn(new CommandRunner.Result(0, "a model answer", "", false));

        var result = service.measure(DIRECTORY, ID, false);

        assertThat(result.succeeded()).isFalse();
        verify(claude, times(1)).execute(any(), any(), anyList());
    }

    @Test
    void rejectsAnIdThatIsNotAUuidWithoutRunningAProcess() {
        var result = service.measure(DIRECTORY, "--help", false);

        assertThat(result.succeeded()).isFalse();
        verify(claude, never()).execute(any(), any(), anyList());
    }

    @Test
    void remembersAFailureBrieflyThenTriesAgain() {
        when(claude.execute(any(), any(), anyList())).thenReturn(new CommandRunner.Result(1, "", "boom", false));

        var result = service.measure(DIRECTORY, ID, false);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure()).contains("/context exited");
        service.measure(DIRECTORY, ID, false);
        verify(claude, times(1)).execute(any(), any(), anyList());
        clock.advance(FAILURE_TTL.plusSeconds(1));
        service.measure(DIRECTORY, ID, false);
        verify(claude, times(2)).execute(any(), any(), anyList());
    }

    @Test
    void concurrentCallsForOneSessionShareOneProcess() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(claude.execute(any(), any(), anyList())).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return new CommandRunner.Result(0, OUTPUT, "", false);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.measure(DIRECTORY, ID, true));
            started.await();
            var second = pool.submit(() -> service.measure(DIRECTORY, ID, true));
            Thread.sleep(200);
            release.countDown();

            assertThat(first.get().succeeded()).isTrue();
            assertThat(second.get().succeeded()).isTrue();
        }
        verify(claude, times(1)).execute(any(), any(), anyList());
    }

    @Test
    void reusesTheResultWithinTheTtlAndForceBypassesIt() {
        service.measure(DIRECTORY, ID, false);
        clock.advance(TTL.minusSeconds(1));
        service.measure(DIRECTORY, ID, false);
        verify(claude, times(1)).execute(any(), any(), anyList());

        service.measure(DIRECTORY, ID, true);
        verify(claude, times(2)).execute(any(), any(), anyList());

        clock.advance(TTL.plusSeconds(1));
        service.measure(DIRECTORY, ID, false);
        verify(claude, times(3)).execute(any(), any(), anyList());
    }

    @Test
    void turnsAnExtensionExceptionIntoAFailure() {
        when(claude.execute(any(), any(), anyList())).thenThrow(new ExtensionException("claude -p timed out after 30 s"));

        var result = service.measure(DIRECTORY, ID, false);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure()).contains("timed out");
    }

    @Test
    void turnsANonZeroExitAndUnparsableOutputIntoFailures() {
        when(claude.execute(any(), any(), anyList())).thenReturn(new CommandRunner.Result(1, "", "boom", false));
        assertThat(service.measure(DIRECTORY, ID, false).failure()).contains("code 1");

        when(claude.execute(any(), any(), anyList())).thenReturn(new CommandRunner.Result(0, "hello", "", false));
        assertThat(service.measure(DIRECTORY, "00000000-0000-0000-0000-000000000002", false).failure()).contains("no context breakdown");
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-08T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
