package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class InstructionApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private final InstructionApplier applier = new InstructionApplier();

    private static PresetComponent instruction(String title, String markdown) {
        return new PresetComponent(1, PresetComponentKind.INSTRUCTION, title, JSON.createObjectNode().put("markdown", markdown));
    }

    private String claudeMd() throws Exception {
        return Files.readString(dir.resolve("CLAUDE.md"));
    }

    @Test
    void sectionIsWrittenBetweenManagedMarkersOfItsSlug() throws Exception {
        applier.apply(instruction("Code Style", "Be terse."), dir);

        assertThat(claudeMd()).contains("<!-- cc-manager:begin code-style -->").contains("## Code Style\nBe terse.")
                .contains("<!-- cc-manager:end code-style -->");
    }

    @Test
    void existingTextIsKeptAndOnlyTheOwnBlockIsReplaced() throws Exception {
        Files.writeString(dir.resolve("CLAUDE.md"), "# Mine\n\nhand text\n");
        applier.apply(instruction("Alpha", "one"), dir);
        applier.apply(instruction("Beta", "two"), dir);
        applier.apply(instruction("Alpha", "changed"), dir);

        assertThat(claudeMd()).startsWith("# Mine\n\nhand text\n").contains("changed").doesNotContain("one\n").contains("## Beta\ntwo");
    }

    @Test
    void textOutsideTheMarkersIsNeverTouchedOnReplace() throws Exception {
        Files.writeString(dir.resolve("CLAUDE.md"), "before\n<!-- cc-manager:begin alpha -->\nold\n<!-- cc-manager:end alpha -->\nafter\n");

        assertThat(applier.plan(instruction("Alpha", "new"), dir).action()).isEqualTo(ChangeAction.UPDATE);
        applier.apply(instruction("Alpha", "new"), dir);

        assertThat(claudeMd()).startsWith("before\n<!-- cc-manager:begin alpha -->").endsWith("<!-- cc-manager:end alpha -->\nafter\n")
                .contains("new").doesNotContain("old");
    }

    @Test
    void sameContentIsPresentAndFileUnchanged() throws Exception {
        applier.apply(instruction("Alpha", "one"), dir);
        var before = claudeMd();

        assertThat(applier.plan(instruction("Alpha", "one"), dir).action()).isEqualTo(ChangeAction.PRESENT);
        applier.apply(instruction("Alpha", "one"), dir);

        assertThat(claudeMd()).isEqualTo(before);
    }

    @Test
    void handWrittenSectionWithSameTitleIsAConflictAndSkippedOnApply() throws Exception {
        Files.writeString(dir.resolve("CLAUDE.md"), "## Alpha\nmy rules\n");

        assertThat(applier.plan(instruction("Alpha", "one"), dir).action()).isEqualTo(ChangeAction.CONFLICT);
        var outcome = applier.apply(instruction("Alpha", "one"), dir);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.SKIPPED);
        assertThat(claudeMd()).isEqualTo("## Alpha\nmy rules\n");
    }

    @Test
    void unclosedMarkersAreAConflictAndNotRewritten() throws Exception {
        Files.writeString(dir.resolve("CLAUDE.md"), "<!-- cc-manager:begin alpha -->\nstuff\n");

        assertThat(applier.apply(instruction("Alpha", "one"), dir).status()).isEqualTo(OutcomeStatus.SKIPPED);
        assertThat(claudeMd()).isEqualTo("<!-- cc-manager:begin alpha -->\nstuff\n");
    }
}
