package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GitLogParserTest {

    @Test
    void aTimezoneOffsetJavaRejectsFallsBackToTheEpoch() {
        assertThat(GitLogParser.instant("2023-11-17T01:13:20+51:00")).isEqualTo(Instant.EPOCH);
    }

    @Test
    void aValidOffsetIsConverted() {
        assertThat(GitLogParser.instant("2023-11-17T01:13:20+01:00")).isEqualTo(Instant.parse("2023-11-17T00:13:20Z"));
    }
}
