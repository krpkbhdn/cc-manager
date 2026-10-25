package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitQuotedPathTest {

    @Test
    void unquotedPathIsReturnedAsIs() {
        assertThat(GitQuotedPath.unquote("src/a.txt")).isEqualTo("src/a.txt");
    }

    @Test
    void escapesAndOctalBytesAreDecoded() {
        assertThat(GitQuotedPath.unquote("\"say \\\"hi\\\"\t\\303\\251.txt\"")).isEqualTo("say \"hi\"\t\u00e9.txt");
    }

    @Test
    void charactersOutsideTheBasicMultilingualPlaneSurviveInAQuotedName() {
        assertThat(GitQuotedPath.unquote("\"say \\\"hi\\\" \uD83C\uDF89.txt\"")).isEqualTo("say \"hi\" \uD83C\uDF89.txt");
    }
}
