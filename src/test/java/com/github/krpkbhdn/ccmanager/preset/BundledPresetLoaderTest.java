package com.github.krpkbhdn.ccmanager.preset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BundledPresetLoaderTest {

    @Test
    void generalIsTheOnlyBundledPresetWithItsComponents() {
        var presets = new BundledPresetLoader().presets();

        assertThat(presets).singleElement().satisfies(general -> {
            assertThat(general.key()).isEqualTo("general");
            assertThat(general.version()).isEqualTo(1);
            assertThat(general.name()).isEqualTo("General");
            assertThat(general.components()).extracting(c -> c.kind() + ":" + c.name()).containsExactly(
                    "INSTRUCTION:Duplicate code", "INSTRUCTION:Clean code", "INSTRUCTION:Comments", "INSTRUCTION:Conventions",
                    "SKILL:jscpd", "SKILL:dry-refactoring", "FILE:.jscpd.json");
            assertThat(general.components().getFirst().payload().path("markdown").asString()).startsWith("- Before writing anything new").doesNotContain("Claude setup");
        });
    }

    @Test
    void componentsPassValidationAndSkillsCarryTheirFiles() {
        var general = new BundledPresetLoader().presets().getFirst();

        general.components().forEach(c -> PresetComponentValidator.validate(c.kind(), c.name(), c.payload()));
        var jscpd = general.components().stream().filter(c -> c.name().equals("jscpd")).findFirst().orElseThrow();
        assertThat(jscpd.payload().path("files").has("SKILL.md")).isTrue();
        var file = general.components().getLast().payload();
        assertThat(file.path("path").asString()).isEqualTo(".jscpd.json");
        assertThat(file.path("content").asString()).contains("minLines").doesNotContain("\"format\"");
        assertThat(file.path("gitignore").get(0).asString()).isEqualTo("jscpd-report/");
    }
}
