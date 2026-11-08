package com.github.krpkbhdn.ccmanager.usage.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import com.github.krpkbhdn.ccmanager.usage.SessionUsage;
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot;
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow;
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService;
import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageRecorderTest {

    private final SessionRegistry registry = mock(SessionRegistry.class);
    private final SessionUsageService usage = mock(SessionUsageService.class);
    private final UsageHistoryRepository repository = mock(UsageHistoryRepository.class);
    private final UsageAlertService alerts = mock(UsageAlertService.class);
    private Runnable registryChanged;
    private final RateLimitService rateLimits = mock(RateLimitService.class);
    private Runnable usageChanged;
    private Runnable rateLimitChanged;

    @BeforeEach
    void setUp() {
        when(registry.subscribe(any())).thenAnswer(call -> {
            registryChanged = call.getArgument(0);
            return (Runnable) () -> { };
        });
        when(usage.subscribe(any())).thenAnswer(call -> {
            usageChanged = call.getArgument(0);
            return (Runnable) () -> { };
        });
        when(rateLimits.subscribe(any())).thenAnswer(call -> {
            rateLimitChanged = call.getArgument(0);
            return (Runnable) () -> { };
        });
        var properties = new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5));
        new UsageRecorder(registry, usage, rateLimits, repository, alerts, properties, Runnable::run);
        when(repository.lastCost("claude-1")).thenReturn(OptionalDouble.of(1.0));
    }

    @Test
    void turnEndWritesOneSampleWithDelta() {
        live(AgentStatus.IDLE);
        live(AgentStatus.WORKING);
        reportsCost(1.5);
        live(AgentStatus.IDLE);
        live(AgentStatus.IDLE);

        var sample = ArgumentCaptor.forClass(UsageSample.class);
        verify(repository, times(1)).insertSample(sample.capture());
        assertThat(sample.getValue().costDelta()).isEqualTo(0.5);
        assertThat(sample.getValue().claudeSessionId()).isEqualTo("claude-1");
        verify(alerts).evaluateDailyBudget();
    }

    @Test
    void claudeSessionIdAppearingLaterKeepsTheCostBaseline() {
        liveWith(AgentStatus.IDLE, null);
        liveWith(AgentStatus.WORKING, null);
        reportsCost(2.0);
        liveWith(AgentStatus.IDLE, null);
        liveWith(AgentStatus.WORKING, "claude-1");
        reportsCost(3.0);
        liveWith(AgentStatus.IDLE, "claude-1");

        var sample = ArgumentCaptor.forClass(UsageSample.class);
        verify(repository, times(2)).insertSample(sample.capture());
        assertThat(sample.getAllValues().get(0).costDelta()).isEqualTo(2.0);
        assertThat(sample.getAllValues().get(1).costDelta()).isEqualTo(1.0);
    }

    @Test
    void removalAfterTurnEndWithUnchangedFiguresWritesNothingMore() {
        live(AgentStatus.IDLE);
        live(AgentStatus.WORKING);
        reportsCost(1.5);
        live(AgentStatus.IDLE);

        when(registry.sessions()).thenReturn(List.of());
        registryChanged.run();

        verify(repository, times(1)).insertSample(any());
    }

    @Test
    void removalWritesLastKnownUsage() {
        live(AgentStatus.WORKING);
        reportsCost(2.0);
        when(registry.sessions()).thenReturn(List.of());
        registryChanged.run();

        var sample = ArgumentCaptor.forClass(UsageSample.class);
        verify(repository).insertSample(sample.capture());
        assertThat(sample.getValue().costUsd()).isEqualTo(2.0);
    }

    @Test
    void endedSessionDoesNotKeepItsInMemoryCostBaseline() {
        live(AgentStatus.IDLE);
        live(AgentStatus.WORKING);
        reportsCost(2.0);
        live(AgentStatus.IDLE);
        when(registry.sessions()).thenReturn(List.of());
        registryChanged.run();

        live(AgentStatus.WORKING);
        reportsCost(5.0);
        live(AgentStatus.IDLE);

        var sample = ArgumentCaptor.forClass(UsageSample.class);
        verify(repository, times(2)).insertSample(sample.capture());
        assertThat(sample.getAllValues().get(1).costDelta()).isEqualTo(4.0);
    }

    @Test
    void shutdownDrainsQueuedSamplesBeforeExit() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var properties = new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5));
        var recorder = new UsageRecorder(registry, usage, rateLimits, repository, alerts, properties, executor);
        var done = new java.util.concurrent.atomic.AtomicBoolean();
        executor.execute(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.set(true);
        });

        recorder.shutdown();

        assertThat(done).isTrue();
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void sessionWithoutCostWritesNothing() {
        live(AgentStatus.WORKING);
        live(AgentStatus.IDLE);

        verify(repository, never()).insertSample(any());
    }

    @Test
    void identicalRateLimitSnapshotsAreStoredOnce() {
        var start = Instant.parse("2030-01-01T10:00:00Z");
        rateLimitAt(50, start);
        rateLimitAt(50, start.plusSeconds(10));

        verify(repository, times(1)).insertRateLimit(any());
    }

    @Test
    void changedPercentIsStored() {
        var start = Instant.parse("2030-01-01T10:00:00Z");
        rateLimitAt(50, start);
        rateLimitAt(51, start.plusSeconds(10));

        verify(repository, times(2)).insertRateLimit(any());
    }

    @Test
    void samePercentAfterTheIntervalIsStored() {
        var start = Instant.parse("2030-01-01T10:00:00Z");
        rateLimitAt(50, start);
        rateLimitAt(50, start.plus(Duration.ofMinutes(5)));

        verify(repository, times(2)).insertRateLimit(any());
    }

    private void rateLimitAt(double percent, Instant at) {
        var window = new RateLimitWindow(percent, at.plus(Duration.ofHours(1)));
        when(rateLimits.current()).thenReturn(Optional.of(new RateLimitSnapshot(window, null, at)));
        rateLimitChanged.run();
    }

    private void reportsCost(double cost) {
        var report = new SessionUsage("ccm-1", "opus", "high", 10.0, cost, null, Instant.now(), null);
        when(usage.of("ccm-1")).thenReturn(Optional.of(report));
        when(usage.all()).thenReturn(Map.of("ccm-1", report));
        usageChanged.run();
    }

    private void live(AgentStatus status) {
        liveWith(status, "claude-1");
    }

    private void liveWith(AgentStatus status, String claudeSessionId) {
        when(registry.sessions()).thenReturn(List.of(new SessionSnapshot("ccm-1", 1L, claudeSessionId, status, null, null,
                null, null, Instant.now(), Instant.now(), List.of(), 0, null)));
        registryChanged.run();
    }

    @Test
    void aFullWriteQueueDropsNewSamplesInsteadOfGrowing() throws Exception {
        var writer = UsageRecorder.boundedWriter(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        var ran = new java.util.concurrent.atomic.AtomicInteger();
        Runnable task = () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ran.incrementAndGet();
        };

        for (var i = 0; i < 10; i++) {
            writer.execute(task);
        }
        release.countDown();
        writer.shutdown();
        writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(ran.get()).isEqualTo(3);
    }
}
