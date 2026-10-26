package com.github.krpkbhdn.ccmanager.claude.plugin;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.springframework.stereotype.Component;

/** Copies the bundled cc-manager plugin (skills for Claude) from the classpath to the data directory on every start, so it never goes stale. */
@Component
public class ClaudePluginWriter {

    private static final String RESOURCE_ROOT = "/claude-plugin/";
    private static final List<String> FILES = List.of(".claude-plugin/plugin.json", "skills/run-configurations/SKILL.md");

    private final Path directory;

    public ClaudePluginWriter(AppProperties properties) {
        this.directory = properties.dataDir().resolve("claude-plugin");
    }

    /** The directory passed as claude --plugin-dir. */
    public Path directory() {
        return directory;
    }

    @PostConstruct
    void write() {
        for (var file : FILES) {
            var target = directory.resolve(file);
            try (var in = ClaudePluginWriter.class.getResourceAsStream(RESOURCE_ROOT + file)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled resource " + RESOURCE_ROOT + file);
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write plugin file " + target, e);
            }
        }
    }
}
