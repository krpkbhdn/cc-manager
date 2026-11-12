package com.github.krpkbhdn.ccmanager.claude.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PtyCommandRunnerTest {

    @Test
    void cleanRemovesColorCursorAndTitleSequencesAndCarriageReturns() {
        var raw = "\u001B[?25l\u001B]0;title\u0007\u001B[1;32mWaiting\u001B[0m for authorization\r\n\u001B[2K\u001B[3;1Hdone";

        assertThat(PtyCommandRunner.clean(raw)).isEqualTo("Waiting for authorization\ndone");
    }

    @Test
    void outputPastTheCapIsDroppedAndMultiByteCharactersSplitAcrossReadsSurvive() {
        var text = "é".repeat(PtyCommandRunner.MAX_RAW_CHARS) + "tail";
        var output = new StringBuilder();

        PtyCommandRunner.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), output, ignored -> { });

        assertThat(output.toString()).isEqualTo("é".repeat(PtyCommandRunner.MAX_RAW_CHARS));
    }
}
