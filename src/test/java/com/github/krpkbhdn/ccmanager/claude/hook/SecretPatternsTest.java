package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"api_key=hunter2", "password: hunter2", "{\"password\":\"hunter2\"}", "{\"api_key\": \"hunter2\"}",
            "Bearer hunter2"})
    void masksTheValue(String text) {
        assertThat(SecretPatterns.KEY_VALUE.matcher(text).replaceAll("$1[redacted]")).contains("[redacted]").doesNotContain("hunter2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain text", "the token is explained below", "ls -la"})
    void leavesPlainTextAlone(String text) {
        assertThat(SecretPatterns.KEY_VALUE.matcher(text).find()).isFalse();
    }
}
