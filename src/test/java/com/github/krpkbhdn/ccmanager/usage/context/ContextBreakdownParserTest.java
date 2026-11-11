package com.github.krpkbhdn.ccmanager.usage.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContextBreakdownParserTest {

    private static final Instant AT = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void parsesHeaderCategoriesAndItemListsOfRealOutput() throws IOException {
        var sample = new String(getClass().getResourceAsStream("/usage/context-sample.md").readAllBytes(), StandardCharsets.UTF_8);

        var breakdown = ContextBreakdownParser.parse(sample, AT).orElseThrow();

        assertThat(breakdown.model()).startsWith("claude-");
        assertThat(breakdown.windowTokens()).isEqualTo(1_000_000);
        assertThat(breakdown.usedTokens()).isPositive();
        assertThat(breakdown.categories()).extracting(ContextBreakdown.Category::name).contains("System prompt", "Messages", "Free space");
        assertThat(breakdown.memoryFiles()).anySatisfy(item -> assertThat(item.name()).contains("CLAUDE.md"));
        assertThat(breakdown.mcpTools()).isNotEmpty().allSatisfy(item -> assertThat(item.group()).isNotBlank());
        assertThat(breakdown.agents()).isNotEmpty();
        assertThat(breakdown.skills()).anySatisfy(item -> assertThat(item.approximate()).isTrue());
        assertThat(breakdown.measuredAt()).isEqualTo(AT);
    }

    @Test
    void readsTokenForms() {
        var markdown = """
                ## Context Usage

                **Model:** m
                **Tokens:** 246.2k / 1m (25%)

                ### Estimated usage by category
                | Category | Tokens | Percentage |
                |----------|--------|------------|
                | A | 599 | 0.1% |
                | B | 2.3k | 1.2% |

                ### Skills
                | Skill | Source | Tokens |
                |-------|--------|--------|
                | s1 | User | ~90 |
                | s2 | Plugin (x) | < 20 |
                """;

        var breakdown = ContextBreakdownParser.parse(markdown, AT).orElseThrow();

        assertThat(breakdown.usedTokens()).isEqualTo(246_200);
        assertThat(breakdown.categories()).extracting(ContextBreakdown.Category::tokens).containsExactly(599L, 2_300L);
        assertThat(breakdown.categories().get(1).percent()).isEqualTo(1.2);
        assertThat(breakdown.skills()).extracting(ContextBreakdown.Item::tokens).containsExactly(90L, 20L);
        assertThat(breakdown.skills()).allMatch(ContextBreakdown.Item::approximate);
    }

    @Test
    void ignoresUnknownSectionsAndToleratesMissingOnes() {
        var markdown = """
                ## Context Usage
                **Tokens:** 10 / 100 (10%)

                ### Something New
                | A | B | 5 |
                """;

        var breakdown = ContextBreakdownParser.parse(markdown, AT).orElseThrow();

        assertThat(breakdown.categories()).isEmpty();
        assertThat(breakdown.mcpTools()).isEmpty();
        assertThat(breakdown.model()).isEmpty();
    }

    @Test
    void keepsUnknownTablesAsSectionsAndRawMarkdown() {
        var markdown = """
                ## Context Usage
                **Tokens:** 10 / 100 (10%)

                ### Hooks
                | Hook | Event | Tokens |
                |------|-------|--------|
                | lint | Stop | ~40 |

                ### Totals
                | Name | Tokens |
                |------|--------|
                | all | 1.5k |

                ### Notes
                | Text |
                |------|
                | nothing countable |
                """;

        var breakdown = ContextBreakdownParser.parse(markdown, AT).orElseThrow();

        assertThat(breakdown.raw()).isEqualTo(markdown);
        assertThat(breakdown.otherSections()).extracting(ContextBreakdown.Section::title).containsExactly("Hooks", "Totals");
        assertThat(breakdown.otherSections().get(0).items()).containsExactly(new ContextBreakdown.Item("lint", "Stop", 40, true));
        assertThat(breakdown.otherSections().get(1).items()).containsExactly(new ContextBreakdown.Item("all", "", 1_500, false));
    }

    @Test
    void realOutputKnownSectionsAreNotRepeatedAsOthers() throws IOException {
        var sample = new String(getClass().getResourceAsStream("/usage/context-sample.md").readAllBytes(), StandardCharsets.UTF_8);

        var breakdown = ContextBreakdownParser.parse(sample, AT).orElseThrow();

        assertThat(breakdown.otherSections()).isEmpty();
        assertThat(breakdown.raw()).isEqualTo(sample);
    }

    @Test
    void rejectsOutputThatIsNotABreakdown() {
        assertThat(ContextBreakdownParser.parse("C:/Program Files/Git/context is a path", AT)).isEmpty();
        assertThat(ContextBreakdownParser.parse("## Context Usage\nno totals", AT)).isEmpty();
        assertThat(ContextBreakdownParser.parse(null, AT)).isEmpty();
    }
}
