package com.github.krpkbhdn.ccmanager.extension.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillFrontmatterTest {

    @Test
    void readsPlainQuotedAndFoldedValues() {
        var content = """
                ---
                name: my-skill
                description: >
                  First line
                  second line
                other: 'quoted: value'
                ---
                # Body
                name: not-frontmatter
                """;

        var fields = SkillFrontmatter.parse(content);

        assertThat(fields).containsEntry("name", "my-skill")
                .containsEntry("description", "First line second line")
                .containsEntry("other", "quoted: value");
    }

    @Test
    void fileWithoutFrontmatterHasNoFields() {
        assertThat(SkillFrontmatter.parse("# Just text\nname: x")).isEmpty();
    }

    @Test
    void renderedTemplateReadsBackEvenWithQuotesInTheDescription() {
        var rendered = SkillFrontmatter.render("review-code", "Use when \"reviewing\": a diff \\ more");

        assertThat(SkillFrontmatter.parse(rendered))
                .containsEntry("name", "review-code")
                .containsEntry("description", "Use when \"reviewing\": a diff \\ more");
    }
}
