package com.github.krpkbhdn.ccmanager.extension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExtensionPropertiesTest {

    @Test
    void acceptsTheDefaults() {
        assertThatCode(() -> new ExtensionProperties(30, 180, 90, 300)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalid")
    void rejectsInvalidValueNamingTheProperty(String field, Supplier<ExtensionProperties> create, String shown) {
        assertThatThrownBy(create::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(kebab(field));
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("queryTimeoutSeconds", (java.util.function.Supplier<ExtensionProperties>) () -> new ExtensionProperties(0, 180, 90, 300), "0"),
                Arguments.of("networkTimeoutSeconds", (java.util.function.Supplier<ExtensionProperties>) () -> new ExtensionProperties(30, -1, 90, 300), "-1"),
                Arguments.of("mcpHealthTimeoutSeconds", (java.util.function.Supplier<ExtensionProperties>) () -> new ExtensionProperties(30, 180, 0, 300), "0"),
                Arguments.of("mcpLoginTimeoutSeconds", (java.util.function.Supplier<ExtensionProperties>) () -> new ExtensionProperties(30, 180, 90, 0), "0"));
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([A-Z])", "-$1").toLowerCase();
    }
}
