package com.github.krpkbhdn.ccmanager.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppControlPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new AppControlProperties(Duration.ofSeconds(10), 20000)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<AppControlProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("uiTimeout", (java.util.function.Supplier<AppControlProperties>) () -> new AppControlProperties(Duration.ZERO, 20000), "Duration.ZERO"),
                Arguments.of("uiTimeout", (java.util.function.Supplier<AppControlProperties>) () -> new AppControlProperties(Duration.ofSeconds(-1), 20000), "Duration.ofSeconds(-1)"),
                Arguments.of("maxTextLength", (java.util.function.Supplier<AppControlProperties>) () -> new AppControlProperties(Duration.ofSeconds(10), 0), "0"),
                Arguments.of("maxTextLength", (java.util.function.Supplier<AppControlProperties>) () -> new AppControlProperties(Duration.ofSeconds(10), -1), "-1"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
