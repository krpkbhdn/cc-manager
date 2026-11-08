package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.recognize.LanguageCode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Voice input tunables: the rolling audio window sent to whisper every tick, the pause that ends an utterance, and the download and idle limits. */
@ConfigurationProperties("cc-manager.voice")
public record VoiceProperties(
        @DefaultValue("auto") String language,
        @DefaultValue("10s") Duration window,
        @DefaultValue("1500ms") Duration tick,
        @DefaultValue("700ms") Duration pause,
        @DefaultValue("0.01") double speechRms,
        @DefaultValue("0.5") double vadThreshold,
        @DefaultValue("4") int threads,
        @DefaultValue("30s") Duration downloadStallTimeout,
        @DefaultValue("5m") Duration idleShutdown,
        @DefaultValue("2m") Duration recognizerOpenTimeout,
        @DefaultValue("60s") Duration flushWait) {

    public VoiceProperties {
        require(LanguageCode.isKnown(language), "language must be \"auto\" or a whisper code such as en, uk, de, got: " + language);
        require(threads > 0,"threads must be positive");
        require(positive(window), "window must be positive");
        require(positive(tick), "tick must be positive");
        require(tick.compareTo(window) <= 0, "tick must not exceed window");
        require(positive(pause), "pause must be positive");
        require(positive(downloadStallTimeout), "downloadStallTimeout must be positive");
        require(positive(recognizerOpenTimeout), "recognizerOpenTimeout must be positive");
        require(positive(flushWait), "flushWait must be positive");
        require(!idleShutdown.isNegative(), "idleShutdown must not be negative");
        require(vadThreshold > 0 && vadThreshold <= 1, "vadThreshold must be in (0, 1]");
        require(speechRms >= 0, "speechRms must not be negative");
    }

    private static boolean positive(Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException("cc-manager.voice." + message);
        }
    }
}
