package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GitPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new GitProperties(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofMillis(300), 2000, 2000, 1048576L)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<GitProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("commandTimeout", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ZERO, Duration.ofMinutes(10), Duration.ofMillis(300), 2000, 2000, 1048576L), "Duration.ZERO"),
                Arguments.of("syncTimeout", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ofSeconds(60), Duration.ZERO, Duration.ofMillis(300), 2000, 2000, 1048576L), "Duration.ZERO"),
                Arguments.of("watchDebounce", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofMillis(-1), 2000, 2000, 1048576L), "Duration.ofMillis(-1)"),
                Arguments.of("maxWatchedDirectories", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofMillis(300), 0, 2000, 1048576L), "0"),
                Arguments.of("maxDiffLines", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofMillis(300), 2000, 0, 1048576L), "0"),
                Arguments.of("maxUntrackedBytes", (java.util.function.Supplier<GitProperties>) () -> new GitProperties(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofMillis(300), 2000, 2000, 0L), "0L"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
