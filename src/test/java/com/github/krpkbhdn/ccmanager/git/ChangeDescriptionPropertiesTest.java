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

class ChangeDescriptionPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new ChangeDescriptionProperties("haiku", Duration.ofSeconds(120), 30000, 4000)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<ChangeDescriptionProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("timeout", (java.util.function.Supplier<ChangeDescriptionProperties>) () -> new ChangeDescriptionProperties("haiku", Duration.ZERO, 30000, 4000), "Duration.ZERO"),
                Arguments.of("contextChars", (java.util.function.Supplier<ChangeDescriptionProperties>) () -> new ChangeDescriptionProperties("haiku", Duration.ofSeconds(120), 0, 4000), "0"),
                Arguments.of("untrackedFileChars", (java.util.function.Supplier<ChangeDescriptionProperties>) () -> new ChangeDescriptionProperties("haiku", Duration.ofSeconds(120), 30000, -1), "-1"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
