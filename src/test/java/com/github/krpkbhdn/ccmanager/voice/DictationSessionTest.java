package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.stream.Dictation;
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener;
import com.github.krpkbhdn.ccmanager.voice.stream.SpeechGate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DictationSessionTest {

    @Test
    void closeWaitsForALastUtteranceThatIsSlowToRecognize() throws Exception {
        var properties = new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(700),
                0.01, 0.5, 4, Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(60));
        var executor = Executors.newSingleThreadExecutor();
        var dictation = new Dictation(mock(Recognizer.class), new SpeechGate(0.01), properties, "en",
                mock(DictationListener.class), executor);
        var recognized = new AtomicBoolean();
        var released = new AtomicBoolean();
        var recognizing = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                recognizing.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recognized.set(true);
        });

        var session = new DictationSession(() -> { }, dictation, executor, () -> released.set(recognized.get()), Duration.ofSeconds(60));
        var closingThread = new AtomicReference<Thread>();
        var closing = CompletableFuture.runAsync(() -> {
            closingThread.set(Thread.currentThread());
            session.close();
        });
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((closingThread.get() == null || !isParked(closingThread.get())) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(isParked(closingThread.get())).isTrue();
        assertThat(closing).isNotDone();
        recognizing.countDown();
        closing.get(10, TimeUnit.SECONDS);

        assertThat(released).isTrue();
    }

    private static boolean isParked(Thread thread) {
        return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING;
    }
}
