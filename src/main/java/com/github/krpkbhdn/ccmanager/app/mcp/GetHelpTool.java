package com.github.krpkbhdn.ccmanager.app.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetHelpTool implements AssistantMcpTool {

    private static final String RESOURCE = "assistant/help.md";
    private static final String HEADING = "## ";

    private final Map<String, String> topics = load();

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_help";
    }

    @Override
    public String description() {
        return "Explains how Claude Code Manager works: one topic, or the list of topics when none is given.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"topic\":{\"type\":\"string\",\"description\":\"One of the topics listed when omitted\"}}}";
    }

    @Override
    public String call(JsonNode arguments) {
        if (!arguments.hasNonNull("topic")) {
            return "Claude Code Manager help. Ask again with one topic: " + String.join(", ", topics.keySet()) + ".";
        }
        var wanted = arguments.path("topic").asString("").strip();
        return topics.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(wanted)).findFirst()
                .map(entry -> entry.getKey() + "\n" + entry.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown topic '" + wanted + "'. Topics: " + String.join(", ", topics.keySet())));
    }

    private static Map<String, String> load() {
        String text;
        try (var stream = new ClassPathResource(RESOURCE).getInputStream()) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing bundled resource " + RESOURCE, e);
        }
        var sections = new LinkedHashMap<String, String>();
        var title = (String) null;
        var body = new ArrayList<String>();
        for (var line : text.lines().toList()) {
            if (line.startsWith(HEADING)) {
                store(sections, title, body);
                title = line.substring(HEADING.length()).strip();
                body.clear();
            } else {
                body.add(line);
            }
        }
        store(sections, title, body);
        return Collections.unmodifiableMap(sections);
    }

    private static void store(Map<String, String> sections, String title, List<String> body) {
        if (title != null) {
            sections.put(title, String.join("\n", body).strip());
        }
    }
}
