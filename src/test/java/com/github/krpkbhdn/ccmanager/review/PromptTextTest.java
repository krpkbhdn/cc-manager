package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptTextTest {

    @Test
    void oneLineReplacesBreaksControlAndBidiCharactersAndCollapsesSpaces() {
        assertThat(PromptText.oneLine("  a\nb\r\nc\u0007d‮e f   g  ")).isEqualTo("a b c d e f g");
    }

    @Test
    void oneLineStripsZeroWidthFormatAndTagCharacters() {
        assertThat(PromptText.oneLine("a​b‎‏⁠﻿c" + new String(Character.toChars(0xE0041)) + "d"))
                .isEqualTo("a b c d");
    }

    @Test
    void withoutHiddenKeepsSpacingButReplacesHiddenCharacters() {
        assertThat(PromptText.withoutHidden("a\n\nb  c")).isEqualTo("a  b  c");
    }
}
