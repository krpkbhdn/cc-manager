package com.github.krpkbhdn.ccmanager.usage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UsagePropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<UsageProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("rawRetention", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ZERO, Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5)), "Duration.ZERO"),
                Arguments.of("rawRetention", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(-1), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5)), "Duration.ofDays(-1)"),
                Arguments.of("retentionInterval", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ZERO, 80, 80, 0, Duration.ofMinutes(5)), "Duration.ZERO"),
                Arguments.of("fiveHourPercent", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), -1, 80, 0, Duration.ofMinutes(5)), "-1"),
                Arguments.of("fiveHourPercent", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 101, 80, 0, Duration.ofMinutes(5)), "101"),
                Arguments.of("sevenDayPercent", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 101, 0, Duration.ofMinutes(5)), "101"),
                Arguments.of("dailyBudgetUsd", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, -1, Duration.ofMinutes(5)), "-1"),
                Arguments.of("rateLimitSampleInterval", (java.util.function.Supplier<UsageProperties>) () -> new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ZERO), "Duration.ZERO"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
