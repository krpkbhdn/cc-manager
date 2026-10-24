package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackgroundAgentParserTest {

    @Test
    void parsesBackgroundEntriesAndSkipsInteractiveOnes() {
        String json = """
                [{"pid":1,"kind":"interactive","sessionId":"s","name":"main","status":"busy"},
                 {"id":"1d6004ad","kind":"background","name":"Docs","state":"done","summary":"ok"}]
                """;

        assertThat(BackgroundAgentParser.parse(json))
                .containsExactly(new BackgroundAgent("1d6004ad", "Docs", "done", "ok"));
    }

    @Test
    void fallsBackToStatusAndEmptySummary() {
        assertThat(BackgroundAgentParser.parse("[{\"id\":\"a\",\"status\":\"busy\"}]"))
                .containsExactly(new BackgroundAgent("a", "", "busy", ""));
    }

    @Test
    void unknownShapesGiveEmptyList() {
        assertThat(BackgroundAgentParser.parse("")).isEmpty();
        assertThat(BackgroundAgentParser.parse("not json")).isEmpty();
        assertThat(BackgroundAgentParser.parse("{\"id\":\"a\"}")).isEmpty();
    }
}
