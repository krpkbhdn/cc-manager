package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.recognize.Transcript;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarmRecognizersTest {

    private static final WarmRecognizers.Key KEY = new WarmRecognizers.Key(WhisperModel.BASE_Q5_1, "cpu");
    private static final Duration IDLE = Duration.ofMillis(100);

    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final List<Runnable> idleStops = new CopyOnWriteArrayList<>();
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();
    private final WarmRecognizers warm = new WarmRecognizers(scheduler, IDLE, Duration.ofMinutes(2));

    @BeforeEach
    void captureIdleStops() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
            idleStops.add(call.getArgument(0));
            return mock(ScheduledFuture.class);
        });
    }

    private Recognizer open() {
        opened.incrementAndGet();
        return new Recognizer() {
            @Override
            public Transcript transcribe(float[] samples, String language) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
    }

    @Test
    void sessionsOfOneKeyShareOneRecognizerAndItClosesOnlyAfterTheLastRelease() throws Exception {
        var first = warm.acquire(KEY, this::open);
        var second = warm.acquire(KEY, this::open);
        assertThat(opened).hasValue(1);

        first.release();
        first.release();
        assertThat(idleStops).isEmpty();

        second.release();
        idleStops.getFirst().run();
        assertThat(closed).hasValue(1);
    }

    @Test
    void leaseAfterIdleShutdownReopensTheRecognizer() throws Exception {
        warm.acquire(KEY, this::open).release();
        idleStops.getFirst().run();

        var lease = warm.acquire(KEY, this::open);

        assertThat(opened).hasValue(2);
        assertThat(lease.recognizer()).isNotNull();
    }

    @Test
    void acquireDuringIdleWaitCancelsTheCloseAndKeepsTheRecognizer() throws Exception {
        warm.acquire(KEY, this::open).release();
        var lease = warm.acquire(KEY, this::open);

        idleStops.getFirst().run();

        assertThat(opened).hasValue(1);
        assertThat(closed).hasValue(0);
        lease.release();
    }

    @Test
    void acquireFailsWhenTheRecognizerDoesNotOpenInTime() throws Exception {
        var shortWait = new WarmRecognizers(scheduler, IDLE, Duration.ofMillis(20));
        var opening = new java.util.concurrent.CountDownLatch(1);
        var openStarted = new java.util.concurrent.CountDownLatch(1);
        var second = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var opener = Executors.newSingleThreadExecutor();
        try {
            opener.submit(() -> shortWait.acquire(KEY, () -> {
                openStarted.countDown();
                try {
                    opening.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return open();
            }));
            assertThat(openStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                shortWait.acquire(KEY, this::open);
            } catch (IllegalStateException e) {
                second.set(e);
            }
        } finally {
            opening.countDown();
            opener.shutdownNow();
        }

        assertThat(second.get()).hasMessageContaining("took longer");
    }

    @Test
    void acquireWaitsForASlowCloseOfTheIdleRecognizerBeforeOpeningAnother() throws Exception {
        var closing = new java.util.concurrent.CountDownLatch(1);
        var closeStarted = new java.util.concurrent.CountDownLatch(1);
        warm.acquire(KEY, () -> new Recognizer() {
            @Override
            public Transcript transcribe(float[] samples, String language) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                closeStarted.countDown();
                try {
                    closing.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).release();
        new Thread(idleStops.getFirst()).start();
        assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var acquiringThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var acquired = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            acquiringThread.set(Thread.currentThread());
            return warm.acquire(KEY, this::open);
        });
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((acquiringThread.get() == null || !isParked(acquiringThread.get())) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        assertThat(isParked(acquiringThread.get())).isTrue();
        assertThat(acquired).isNotDone();
        assertThat(opened).hasValue(0);

        closing.countDown();

        assertThat(acquired.get(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(opened).hasValue(1);
    }

    private static boolean isParked(Thread thread) {
        return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING;
    }

    @Test
    void shutdownClosesLiveRecognizerOnceAndLateReleaseDoesNotCloseAgain() throws Exception {
        var lease = warm.acquire(KEY, this::open);

        warm.shutdown();
        lease.release();
        warm.shutdown();

        assertThat(closed).hasValue(1);
        assertThatThrownBy(() -> warm.acquire(KEY, this::open)).isInstanceOf(IllegalStateException.class);
    }
}
