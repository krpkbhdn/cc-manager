package com.github.krpkbhdn.ccmanager.update;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UpdatePropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new UpdateProperties("", "o/r", "http://x", 6, 15)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<UpdateProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("checkIntervalHours", (java.util.function.Supplier<UpdateProperties>) () -> new UpdateProperties("", "o/r", "http://x", 0, 15), "0"),
                Arguments.of("checkIntervalHours", (java.util.function.Supplier<UpdateProperties>) () -> new UpdateProperties("", "o/r", "http://x", -1, 15), "-1"),
                Arguments.of("requestTimeoutSeconds", (java.util.function.Supplier<UpdateProperties>) () -> new UpdateProperties("", "o/r", "http://x", 6, 0), "0"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
