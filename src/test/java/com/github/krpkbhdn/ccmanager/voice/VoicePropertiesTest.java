package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class VoicePropertiesTest {

    @Test
    void acceptsValidValues() {
        assertThat(create(4, Duration.ofSeconds(10), Duration.ofSeconds(1), 0.5)).isNotNull();
    }

    @Test
    void rejectsNonPositiveThreads() {
        assertThatThrownBy(() -> create(0, Duration.ofSeconds(10), Duration.ofSeconds(1), 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("threads");
    }

    @Test
    void rejectsTickLongerThanWindow() {
        assertThatThrownBy(() -> create(4, Duration.ofSeconds(1), Duration.ofSeconds(2), 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tick");
    }

    @Test
    void rejectsZeroWindow() {
        assertThatThrownBy(() -> create(4, Duration.ZERO, Duration.ZERO, 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("window");
    }

    @Test
    void rejectsZeroPauseAndZeroDownloadStallTimeout() {
        assertThatThrownBy(() -> new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ZERO,
                0.01, 0.5, 4, Duration.ofSeconds(30), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pause");
        assertThatThrownBy(() -> new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(700),
                0.01, 0.5, 4, Duration.ZERO, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("downloadStallTimeout");
    }

    @Test
    void rejectsVadThresholdOutsideZeroToOne() {
        assertThatThrownBy(() -> create(4, Duration.ofSeconds(10), Duration.ofSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(4, Duration.ofSeconds(10), Duration.ofSeconds(1), 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsALanguageWhisperDoesNotKnow() {
        assertThatThrownBy(() -> withLanguage("ua")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language");
        assertThatThrownBy(() -> withLanguage("xx")).isInstanceOf(IllegalArgumentException.class);
        assertThat(withLanguage("uk")).isNotNull();
        assertThat(withLanguage("en")).isNotNull();
        assertThat(withLanguage("auto")).isNotNull();
    }

    private static VoiceProperties withLanguage(String language) {
        return new VoiceProperties(language, Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(700), 0.01, 0.5, 4,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
    }

    private static VoiceProperties create(int threads, Duration window, Duration tick, double vadThreshold) {
        return new VoiceProperties("auto", window, tick, Duration.ofMillis(700), 0.01, vadThreshold, threads,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
    }
}
