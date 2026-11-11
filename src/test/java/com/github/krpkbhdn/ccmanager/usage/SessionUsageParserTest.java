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

        assertThat(usage).isEqualTo(new SessionUsage("ccm-1", "Opus 4.7", "high", 42.5, 1.25, 90_000L, NOW, null));
    }

    @Test
    void readsContextWindowTokens() {
        var context = parse("{\"context_window\":{\"context_window_size\":200000,\"used_percentage\":42.5,"
                + "\"total_input_tokens\":900,\"total_output_tokens\":300,\"current_usage\":{\"input_tokens\":10,"
                + "\"output_tokens\":20,\"cache_creation_input_tokens\":30,\"cache_read_input_tokens\":40}}}").context();

        assertThat(context).isEqualTo(new ContextWindow(200_000L, 10L, 30L, 40L, 20L, 900L, 300L));
        assertThat(context.usedTokens()).isEqualTo(80L);
    }

    @Test
    void nullCurrentUsageKeepsPercentAndLeavesTokensNull() {
        var usage = parse("{\"context_window\":{\"context_window_size\":200000,\"used_percentage\":5,\"current_usage\":null}}");

        assertThat(usage.contextUsedPercent()).isEqualTo(5.0);
        assertThat(usage.context().windowSize()).isEqualTo(200_000L);
        assertThat(usage.context().usedTokens()).isNull();
        assertThat(usage.context().outputTokens()).isNull();
    }

    @Test
    void missingContextWindowGivesNullContext() {
        assertThat(parse("{\"context_window\":\"x\"}").context()).isNull();
        assertThat(parse("{}").context()).isNull();
    }

    @Test
    void mistypedTokenFieldsBecomeNull() {
        var context = parse("{\"context_window\":{\"context_window_size\":\"big\",\"current_usage\":{\"input_tokens\":\"1\","
                + "\"cache_read_input_tokens\":7}}}").context();

        assertThat(context.windowSize()).isNull();
        assertThat(context.inputTokens()).isNull();
        assertThat(context.usedTokens()).isEqualTo(7L);
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
