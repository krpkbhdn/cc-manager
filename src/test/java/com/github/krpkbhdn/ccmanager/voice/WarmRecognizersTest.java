package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.recognize.Transcript;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WarmRecognizersTest {

    private static final WarmRecognizers.Key KEY = new WarmRecognizers.Key(WhisperModel.BASE_Q5_1, "cpu");
    private static final Duration IDLE = Duration.ofMillis(100);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();
    private final WarmRecognizers warm = new WarmRecognizers(scheduler, IDLE);

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
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

    private void awaitClosed(int expected) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (closed.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void sessionsOfOneKeyShareOneRecognizerAndItClosesOnlyAfterTheLastRelease() throws Exception {
        var first = warm.acquire(KEY, this::open);
        var second = warm.acquire(KEY, this::open);
        assertThat(opened).hasValue(1);

        first.release();
        first.release();
        Thread.sleep(IDLE.toMillis() * 3);
        assertThat(closed).hasValue(0);

        second.release();
        awaitClosed(1);
        assertThat(closed).hasValue(1);
    }

    @Test
    void leaseAfterIdleShutdownReopensTheRecognizer() throws Exception {
        warm.acquire(KEY, this::open).release();
        awaitClosed(1);

        var lease = warm.acquire(KEY, this::open);

        assertThat(opened).hasValue(2);
        assertThat(lease.recognizer()).isNotNull();
    }

    @Test
    void acquireDuringIdleWaitCancelsTheCloseAndKeepsTheRecognizer() throws Exception {
        warm.acquire(KEY, this::open).release();
        var lease = warm.acquire(KEY, this::open);

        Thread.sleep(IDLE.toMillis() * 3);

        assertThat(opened).hasValue(1);
        assertThat(closed).hasValue(0);
        lease.release();
    }

    @Test
    void shutdownClosesLiveRecognizerOnceAndLateReleaseDoesNotCloseAgain() throws Exception {
        var lease = warm.acquire(KEY, this::open);

        warm.shutdown();
        lease.release();
        warm.shutdown();
        Thread.sleep(IDLE.toMillis() * 2);

        assertThat(closed).hasValue(1);
        assertThatThrownBy(() -> warm.acquire(KEY, this::open)).isInstanceOf(IllegalStateException.class);
    }
}
