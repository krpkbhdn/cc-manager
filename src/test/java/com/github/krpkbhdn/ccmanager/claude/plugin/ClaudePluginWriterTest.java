package com.github.krpkbhdn.ccmanager.claude.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudePluginWriterTest {

    @TempDir
    Path dataDir;

    @Test
    void copiesTheManifestAndSkillAndOverwritesStaleCopies() throws Exception {
        var writer = new ClaudePluginWriter(new AppProperties(dataDir));
        var skill = writer.directory().resolve("skills/run-configurations/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "stale");

        writer.write();

        assertThat(writer.directory()).isEqualTo(dataDir.resolve("claude-plugin"));
        assertThat(Files.readString(writer.directory().resolve(".claude-plugin/plugin.json"))).contains("\"name\": \"cc-manager\"");
        assertThat(Files.readString(skill)).startsWith("---").contains("name: run-configurations");
    }
}
