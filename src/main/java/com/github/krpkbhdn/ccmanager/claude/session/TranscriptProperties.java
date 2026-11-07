package com.github.krpkbhdn.ccmanager.claude.session;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Transcript reading limits: entries returned, bytes read from the file tail on first and later title reads, and the byte window around a jump target. */
@ConfigurationProperties("cc-manager.transcript")
public record TranscriptProperties(
        @DefaultValue("300") int maxEntries,
        @DefaultValue("1048576") int tailBytes,
        @DefaultValue("65536") int refreshTailBytes,
        @DefaultValue("524288") int windowBytes) {

    public TranscriptProperties {
        positive("cc-manager.transcript.max-entries", maxEntries);
        positive("cc-manager.transcript.tail-bytes", tailBytes);
        positive("cc-manager.transcript.refresh-tail-bytes", refreshTailBytes);
        positive("cc-manager.transcript.window-bytes", windowBytes);
    }
}
