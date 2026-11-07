package com.github.krpkbhdn.ccmanager.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    @Test
    void keepsTheDefaultValues() {
        var properties = new NotificationProperties(200, Duration.ofSeconds(2));

        assertThat(properties.historyLimit()).isEqualTo(200);
        assertThat(properties.duplicateWindow()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void rejectsNonPositiveHistoryLimitNamingTheProperty() {
        assertThatThrownBy(() -> new NotificationProperties(0, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("history-limit");
    }

    @Test
    void rejectsNegativeDuplicateWindowNamingTheProperty() {
        assertThatThrownBy(() -> new NotificationProperties(200, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate-window");
    }
}
