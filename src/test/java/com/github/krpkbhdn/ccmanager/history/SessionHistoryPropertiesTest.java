package com.github.krpkbhdn.ccmanager.history;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SessionHistoryPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new SessionHistoryProperties(Duration.ofSeconds(30), 200000, 100, 5000, 1048576L, 300, 1000)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<SessionHistoryProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("scanInterval", (java.util.function.Supplier<SessionHistoryProperties>) () -> new SessionHistoryProperties(Duration.ZERO, 200000, 100, 5000, 1048576L, 300, 1000), "Duration.ZERO"),
                Arguments.of("maxEntries", (java.util.function.Supplier<SessionHistoryProperties>) () -> new SessionHistoryProperties(Duration.ofSeconds(30), 0, 100, 5000, 1048576L, 300, 1000), "0"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
