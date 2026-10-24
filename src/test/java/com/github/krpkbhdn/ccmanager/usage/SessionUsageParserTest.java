package com.github.krpkbhdn.ccmanager.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionUsageParserTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private SessionUsage parse(String json) {
        return SessionUsageParser.parse("ccm-1", mapper.readTree(json), NOW);
    }

    @Test
    void readsTheDocumentedFields() {
        var usage = parse("{\"model\":{\"id\":\"claude-opus-4-7\",\"display_name\":\"Opus 4.7\"},\"effort\":{\"level\":\"high\"},"
                + "\"context_window\":{\"used_percentage\":42.5},\"cost\":{\"total_cost_usd\":1.25,\"total_duration_ms\":90000}}");

        assertThat(usage).isEqualTo(new SessionUsage("ccm-1", "Opus 4.7", "high", 42.5, 1.25, 90_000L, NOW));
    }

    @Test
    void fallsBackToTheModelIdWithoutDisplayName() {
        assertThat(parse("{\"model\":{\"id\":\"claude-haiku-5-5\"}}").modelName()).isEqualTo("claude-haiku-5-5");
    }

    @Test
    void missingAndMistypedFieldsBecomeNull() {
        var usage = parse("{\"context_window\":{\"used_percentage\":null},\"cost\":\"free\",\"effort\":{}}");

        assertThat(usage.modelName()).isNull();
        assertThat(usage.effort()).isNull();
        assertThat(usage.contextUsedPercent()).isNull();
        assertThat(usage.costUsd()).isNull();
        assertThat(usage.durationMillis()).isNull();
    }
}
