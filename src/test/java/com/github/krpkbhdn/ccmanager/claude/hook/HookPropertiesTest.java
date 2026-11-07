package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HookPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new HookProperties(50000, 1024, 500, Duration.ofSeconds(590), 50, 2000)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<HookProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("retention", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(0, 1024, 500, Duration.ofSeconds(590), 50, 2000), "0"),
                Arguments.of("retention", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(-1, 1024, 500, Duration.ofSeconds(590), 50, 2000), "-1"),
                Arguments.of("maxBodyBytes", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(50000, 0, 500, Duration.ofSeconds(590), 50, 2000), "0"),
                Arguments.of("maxStoredTextLength", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(50000, 1024, 0, Duration.ofSeconds(590), 50, 2000), "0"),
                Arguments.of("decisionHold", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(50000, 1024, 500, Duration.ZERO, 50, 2000), "Duration.ZERO"),
                Arguments.of("maxHeldProposals", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(50000, 1024, 500, Duration.ofSeconds(590), -1, 2000), "-1"),
                Arguments.of("maxScannedEvents", (java.util.function.Supplier<HookProperties>) () -> new HookProperties(50000, 1024, 500, Duration.ofSeconds(590), 50, 0), "0"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
