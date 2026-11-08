package com.github.krpkbhdn.ccmanager.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationSoundPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new NotificationSoundProperties(true, 0.9, Duration.ofSeconds(1), Duration.ofMillis(500))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<NotificationSoundProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("volume", (java.util.function.Supplier<NotificationSoundProperties>) () -> new NotificationSoundProperties(true, -0.1, Duration.ofSeconds(1), Duration.ofMillis(500)), "-0.1"),
                Arguments.of("volume", (java.util.function.Supplier<NotificationSoundProperties>) () -> new NotificationSoundProperties(true, 1.1, Duration.ofSeconds(1), Duration.ofMillis(500)), "1.1"),
                Arguments.of("minInterval", (java.util.function.Supplier<NotificationSoundProperties>) () -> new NotificationSoundProperties(true, 0.9, Duration.ofMillis(-1), Duration.ofMillis(500)), "Duration.ofMillis(-1)"),
                Arguments.of("lineTail", (java.util.function.Supplier<NotificationSoundProperties>) () -> new NotificationSoundProperties(true, 0.9, Duration.ofSeconds(1), Duration.ofMillis(-1)), "Duration.ofMillis(-1)"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
