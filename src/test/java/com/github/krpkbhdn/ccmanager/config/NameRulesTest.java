package com.github.krpkbhdn.ccmanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NameRulesTest {

    @Test
    void returnsTheStrippedName() {
        assertThat(NameRules.requireName("  Dev  ", 5)).isEqualTo("Dev");
        assertThat(NameRules.requireName("12345", 5)).isEqualTo("12345");
    }

    @Test
    void nullAndBlankNamesAreRefusedWithTheEmptyMessage() {
        assertThatThrownBy(() -> NameRules.requireName(null, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The name must not be empty.");
        assertThatThrownBy(() -> NameRules.requireName("   ", 5)).hasMessage("The name must not be empty.");
    }

    @Test
    void tooLongNameIsRefusedWithTheLimitInTheMessage() {
        assertThatThrownBy(() -> NameRules.requireName("123456", 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The name is longer than 5 characters.");
    }
}
