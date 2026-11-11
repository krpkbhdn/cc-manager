package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeMdSectionsTest {

    @Test
    void slugIsKebabCase() {
        assertThat(ClaudeMdSections.slug("  Code Style & Rules! ")).isEqualTo("code-style-rules");
    }

    @Test
    void emptyFileGetsTheBlock() {
        var plan = ClaudeMdSections.plan("", "Style", "Use tabs.");

        assertThat(plan.action()).isEqualTo(ChangeAction.CREATE);
        assertThat(plan.text()).isEqualTo("<!-- cc-manager:begin style -->\n## Style\nUse tabs.\n<!-- cc-manager:end style -->\n");
    }

    @Test
    void blockIsAppendedAfterABlankLine() {
        var plan = ClaudeMdSections.plan("# Mine", "Style", "Use tabs.");

        assertThat(plan.text()).startsWith("# Mine\n\n<!-- cc-manager:begin style -->");
    }

    @Test
    void identicalBlockIsPresent() {
        var file = ClaudeMdSections.plan("# Mine\n", "Style", "Use tabs.").text();

        assertThat(ClaudeMdSections.plan(file, "Style", "Use tabs.").action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void changedBlockIsReplacedOnlyBetweenItsMarkers() {
        var file = "# Mine\n\n" + ClaudeMdSections.block("Style", "Use tabs.") + "\n\nmy tail\n";

        var plan = ClaudeMdSections.plan(file, "Style", "Use spaces.");

        assertThat(plan.action()).isEqualTo(ChangeAction.UPDATE);
        assertThat(plan.text()).isEqualTo("# Mine\n\n" + ClaudeMdSections.block("Style", "Use spaces.") + "\n\nmy tail\n");
    }

    @Test
    void handWrittenHeadingIsAConflict() {
        var plan = ClaudeMdSections.plan("## Style\nmine\n", "Style", "Use tabs.");

        assertThat(plan.action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(plan.text()).isNull();
    }

    @Test
    void headingInsideAnotherManagedBlockIsNotAConflict() {
        var file = "<!-- cc-manager:begin other -->\n## Other\n## Style\n<!-- cc-manager:end other -->\n";

        assertThat(ClaudeMdSections.plan(file, "Style", "x").action()).isEqualTo(ChangeAction.CREATE);
    }

    @Test
    void unclosedBeginMarkerIsAConflictAndNeverGetsASecondBlock() {
        var plan = ClaudeMdSections.plan("# Mine\n<!-- cc-manager:begin style -->\n## Style\nold\n", "Style", "new");

        assertThat(plan.action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(plan.text()).isNull();
    }

    @Test
    void missingBeginOrDuplicatedMarkersAreConflicts() {
        var block = ClaudeMdSections.block("Style", "old");

        assertThat(ClaudeMdSections.plan("<!-- cc-manager:end style -->\n", "Style", "x").action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(ClaudeMdSections.plan(block + "\n" + block + "\n", "Style", "x").action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(ClaudeMdSections.plan("<!-- cc-manager:end style -->\n<!-- cc-manager:begin style -->\n", "Style", "x").action()).isEqualTo(ChangeAction.CONFLICT);
    }

    @Test
    void markersInsideFencedCodeAreIgnored() {
        var file = "# Docs\n\n```md\n<!-- cc-manager:begin style -->\n## Style\n<!-- cc-manager:end style -->\n```\n";

        var plan = ClaudeMdSections.plan(file, "Style", "x");

        assertThat(plan.action()).isEqualTo(ChangeAction.CREATE);
        assertThat(plan.text()).startsWith(file).endsWith("<!-- cc-manager:end style -->\n");
    }

    @Test
    void realBlockAfterAFenceIsStillFoundAndUpdated() {
        var file = "~~~\n<!-- cc-manager:begin style -->\n~~~\n" + ClaudeMdSections.block("Style", "old") + "\n";

        assertThat(ClaudeMdSections.plan(file, "Style", "new").action()).isEqualTo(ChangeAction.UPDATE);
    }

    @Test
    void instructionContainingMarkersIsNeverWritten() {
        var plan = ClaudeMdSections.plan("", "Style", "x\n<!-- cc-manager:end style -->\nevil");

        assertThat(plan.action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(plan.text()).isNull();
    }

    @Test
    void crlfFileKeepsCrlf() {
        var plan = ClaudeMdSections.plan("# Mine\r\n", "Style", "a\nb");

        assertThat(plan.text()).isEqualTo("# Mine\r\n\r\n<!-- cc-manager:begin style -->\r\n## Style\r\na\r\nb\r\n<!-- cc-manager:end style -->\r\n");
        assertThat(ClaudeMdSections.plan(plan.text(), "Style", "a\nb").action()).isEqualTo(ChangeAction.PRESENT);
        assertThat(ClaudeMdSections.plan(plan.text(), "Style", "c").text()).doesNotContainPattern("[^\\r]\\n");
    }
}
