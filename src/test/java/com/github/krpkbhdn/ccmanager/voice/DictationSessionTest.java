package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.stream.Dictation;
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener;
import com.github.krpkbhdn.ccmanager.voice.stream.SpeechGate;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DictationSessionTest {

    private static final Duration SLOWER_THAN_THE_OLD_FLUSH_CAP = Duration.ofMillis(2500);

    @Test
    void closeWaitsForALastUtteranceThatIsSlowToRecognize() {
        var properties = new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(700),
                0.01, 0.5, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        var executor = Executors.newSingleThreadExecutor();
        var dictation = new Dictation(mock(Recognizer.class), new SpeechGate(0.01), properties, "en",
                mock(DictationListener.class), executor);
        var recognized = new AtomicBoolean();
        var released = new AtomicBoolean();
        executor.execute(() -> {
            try {
                Thread.sleep(SLOWER_THAN_THE_OLD_FLUSH_CAP.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recognized.set(true);
        });

        new DictationSession(() -> { }, dictation, executor, () -> released.set(recognized.get())).close();

        assertThat(released).isTrue();
    }
}
