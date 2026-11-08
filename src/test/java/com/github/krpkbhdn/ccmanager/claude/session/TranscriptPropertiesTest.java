package com.github.krpkbhdn.ccmanager.claude.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TranscriptPropertiesTest {

    @Test
    void keepsTheDefaultValues() {
        var properties = new TranscriptProperties(300, 1048576, 65536, 524288);

        assertThat(properties.maxEntries()).isEqualTo(300);
        assertThat(properties.tailBytes()).isEqualTo(1 << 20);
        assertThat(properties.refreshTailBytes()).isEqualTo(64 * 1024);
        assertThat(properties.windowBytes()).isEqualTo(512 * 1024);
    }

    @Test
    void rejectsNonPositiveValuesNamingTheProperty() {
        assertThatThrownBy(() -> new TranscriptProperties(0, 1, 1, 1)).hasMessageContaining("max-entries");
        assertThatThrownBy(() -> new TranscriptProperties(1, 0, 1, 1)).hasMessageContaining("tail-bytes");
        assertThatThrownBy(() -> new TranscriptProperties(1, 1, 0, 1)).hasMessageContaining("refresh-tail-bytes");
        assertThatThrownBy(() -> new TranscriptProperties(1, 1, 1, 0)).hasMessageContaining("window-bytes");
    }
}
