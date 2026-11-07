package com.github.krpkbhdn.ccmanager.github;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GhPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new GhProperties("gh", Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ofMinutes(10))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<GhProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("timeout", (java.util.function.Supplier<GhProperties>) () -> new GhProperties("gh", Duration.ZERO, Duration.ofSeconds(20), Duration.ofMinutes(10)), "Duration.ZERO"),
                Arguments.of("ciPollInterval", (java.util.function.Supplier<GhProperties>) () -> new GhProperties("gh", Duration.ofSeconds(60), Duration.ZERO, Duration.ofMinutes(10)), "Duration.ZERO"),
                Arguments.of("ciPollInterval", (java.util.function.Supplier<GhProperties>) () -> new GhProperties("gh", Duration.ofSeconds(60), Duration.ofSeconds(-1), Duration.ofMinutes(10)), "Duration.ofSeconds(-1)"),
                Arguments.of("readyTtl", (java.util.function.Supplier<GhProperties>) () -> new GhProperties("gh", Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ZERO), "Duration.ZERO"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
