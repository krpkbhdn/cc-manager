package com.github.krpkbhdn.ccmanager.preset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the bundled presets from the classpath; a broken file is a build defect, so it fails fast with the resource named.
 * Coupling: the General preset's skill sources point into {@code author-profile/user/skills}, so those folders must stay on the classpath.
 */
@Component
public class BundledPresetLoader {

    private static final String RESOURCE = "presets/presets.json";
    private static final JsonMapper MAPPER = PresetComponentValidator.MAPPER;

    private record Document(List<RawPreset> presets) {
    }

    private record RawPreset(String key, int version, String name, String description, List<RawComponent> components) {
    }

    /** @param source classpath file (INSTRUCTION markdown, FILE content) or folder (SKILL files) holding the text of the component */
    private record RawComponent(PresetComponentKind kind, String name, String source, JsonNode payload) {
    }

    private final List<BundledPreset> presets;

    public BundledPresetLoader() {
        try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.presets = MAPPER.readValue(in, Document.class).presets().stream().map(BundledPresetLoader::resolve).toList();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Bundled presets cannot be loaded from " + RESOURCE + ": " + e.getMessage(), e);
        }
    }

    BundledPresetLoader(List<BundledPreset> presets) {
        this.presets = List.copyOf(presets);
    }

    public List<BundledPreset> presets() {
        return presets;
    }

    private static BundledPreset resolve(RawPreset raw) {
        var components = raw.components().stream().map(BundledPresetLoader::resolve).toList();
        return new BundledPreset(raw.key(), raw.version(), raw.name(), raw.description(), components);
    }

    private static PresetComponentSpec resolve(RawComponent raw) {
        var payload = switch (raw.kind()) {
            case INSTRUCTION -> MAPPER.createObjectNode().put("markdown", text(raw.source()));
            case SKILL -> MAPPER.createObjectNode().set("files", MAPPER.valueToTree(folder(raw.source())));
            case FILE -> MAPPER.createObjectNode().setAll((ObjectNode) raw.payload()).put("content", text(raw.source()));
            default -> raw.payload();
        };
        return new PresetComponentSpec(raw.kind(), raw.name(), payload);
    }

    private static String text(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Bundled preset source is missing on the classpath: " + path, e);
        }
    }

    private static TreeMap<String, String> folder(String dir) {
        var files = new TreeMap<String, String>();
        var marker = "/" + dir + "/";
        try {
            for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath:" + dir + "/**")) {
                var url = resource.getURL().toString();
                if (!url.endsWith("/")) {
                    var relative = url.substring(url.indexOf(marker) + marker.length());
                    files.put(relative, text(dir + "/" + relative));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Bundled preset folder cannot be read: " + dir, e);
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No files in bundled preset folder " + dir);
        }
        return files;
    }
}
