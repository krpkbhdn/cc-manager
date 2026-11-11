package com.github.krpkbhdn.ccmanager.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"api_key=hunter2", "password: hunter2", "{\"password\":\"hunter2\"}", "{\"api_key\": \"hunter2\"}",
            "Bearer hunter2"})
    void masksTheValue(String text) {
        assertThat(SecretPatterns.KEY_VALUE.matcher(text).replaceAll("$1[redacted]")).contains("[redacted]").doesNotContain("hunter2");
    }

    @Test
    void keepsTheKeyAndMasksQuotedValuesWithSpaces() {
        assertThat(SecretPatterns.KEY_VALUE.matcher("token='a b c' rest").replaceAll("$1[redacted]")).isEqualTo("token=[redacted] rest");
    }

    @Test
    void doesNotMaskSpaceSeparatedFlags() {
        assertThat(SecretPatterns.KEY_VALUE.matcher("run --password hunter2").find()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain text", "the token is explained below", "ls -la"})
    void leavesPlainTextAlone(String text) {
        assertThat(SecretPatterns.KEY_VALUE.matcher(text).find()).isFalse();
    }
}
