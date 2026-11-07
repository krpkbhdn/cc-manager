package com.github.krpkbhdn.ccmanager.claude.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SessionPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(10))).doesNotThrowAnyException();
    }

    @Test
    void acceptsZeroToDisableStaleness() {
        assertThatCode(() -> new SessionProperties(Duration.ZERO, Duration.ZERO)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<SessionProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("subagentStaleAfter", (java.util.function.Supplier<SessionProperties>) () -> new SessionProperties(Duration.ofMinutes(-1), Duration.ofMinutes(10)), "Duration.ofMinutes(-1)"),
                Arguments.of("workingStaleAfter", (java.util.function.Supplier<SessionProperties>) () -> new SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(-1)), "Duration.ofMinutes(-1)"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
