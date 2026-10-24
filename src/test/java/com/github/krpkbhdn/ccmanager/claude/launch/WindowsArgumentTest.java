package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WindowsArgumentTest {

    @Test
    void plainArgumentIsLeftAlone() {
        assertThat(WindowsArgument.quote("--model")).isEqualTo("--model");
    }

    @Test
    void jsonQuotesAreEscapedInsideOneQuotedArgument() {
        assertThat(WindowsArgument.quote("{\"a\": \"b\"}")).isEqualTo("\"{\\\"a\\\": \\\"b\\\"}\"");
    }

    @Test
    void backslashesBeforeQuotesAndAtTheEndAreDoubled() {
        assertThat(WindowsArgument.quote("a\\\"b c\\")).isEqualTo("\"a\\\\\\\"b c\\\\\"");
    }

    @Test
    void newlinesForceQuoting() {
        assertThat(WindowsArgument.quote("x\ny")).isEqualTo("\"x\ny\"");
    }
}
