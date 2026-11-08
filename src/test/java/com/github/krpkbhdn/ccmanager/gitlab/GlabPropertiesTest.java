package com.github.krpkbhdn.ccmanager.gitlab;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GlabPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new GlabProperties("glab", Duration.ofSeconds(60), Duration.ofMinutes(10))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveTimeoutNamingTheProperty() {
        assertThatThrownBy(() -> new GlabProperties("glab", Duration.ZERO, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeout");
    }

    @Test
    void rejectsNonPositiveReadyTtlNamingTheProperty() {
        assertThatThrownBy(() -> new GlabProperties("glab", Duration.ofSeconds(60), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ready-ttl");
    }
}
