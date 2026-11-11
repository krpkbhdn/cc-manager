package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SkillApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private final SkillApplier applier = new SkillApplier();

    private static PresetComponent skill(String name, String... pathAndContent) {
        var files = JSON.createObjectNode();
        for (var i = 0; i < pathAndContent.length; i += 2) {
            files.put(pathAndContent[i], pathAndContent[i + 1]);
        }
        return new PresetComponent(1, PresetComponentKind.SKILL, name, JSON.createObjectNode().set("files", files));
    }

    @Test
    void missingSkillIsCreatedWithAllItsFiles() throws Exception {
        var component = skill("demo", "SKILL.md", "body", "scripts/run.sh", "echo");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.CREATE);
        applier.apply(component, dir);

        assertThat(Files.readString(dir.resolve(".claude/skills/demo/SKILL.md"))).isEqualTo("body");
        assertThat(Files.readString(dir.resolve(".claude/skills/demo/scripts/run.sh"))).isEqualTo("echo");
    }

    @Test
    void existingDifferentSkillIsAConflictAndLeftAlone() throws Exception {
        Files.createDirectories(dir.resolve(".claude/skills/demo"));
        Files.writeString(dir.resolve(".claude/skills/demo/SKILL.md"), "mine");

        assertThat(applier.plan(skill("demo", "SKILL.md", "other"), dir).action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(Files.readString(dir.resolve(".claude/skills/demo/SKILL.md"))).isEqualTo("mine");
    }

    @Test
    void identicalSkillIsPresent() throws Exception {
        applier.apply(skill("demo", "SKILL.md", "body"), dir);

        assertThat(applier.plan(skill("demo", "SKILL.md", "body"), dir).action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void skillFileLeavingTheSkillFolderIsRejectedAndNothingIsLeftBehind() {
        for (var bad : new String[] {"../x.md", "../../x.md", "/abs.md", "C:/x.md", ".git/x"}) {
            var component = skill("demo", "SKILL.md", "ok", bad, "x");
            assertThatThrownBy(() -> applier.apply(component, dir)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(dir.resolve(".claude/skills/demo")).doesNotExist();
        assertThat(dir.resolve(".claude/skills/x.md")).doesNotExist();
    }

    @Test
    void skillNameWithTraversalIsRejected() {
        assertThatThrownBy(() -> applier.plan(skill("../../evil", "SKILL.md", "x"), dir)).isInstanceOf(IllegalArgumentException.class);
    }
}
