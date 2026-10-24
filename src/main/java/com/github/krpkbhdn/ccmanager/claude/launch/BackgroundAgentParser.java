package com.github.krpkbhdn.ccmanager.claude.launch;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Defensive parser of claude agents --json; entries without an attachable id (interactive sessions) are skipped. */
final class BackgroundAgentParser {

    private static final Logger log = LoggerFactory.getLogger(BackgroundAgentParser.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private BackgroundAgentParser() {}

    static List<BackgroundAgent> parse(String json) {
        List<BackgroundAgent> agents = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                root.forEach(node -> addIfAttachable(node, agents));
            }
        } catch (RuntimeException e) {
            log.debug("Unrecognised claude agents output", e);
            return List.of();
        }
        return List.copyOf(agents);
    }

    private static void addIfAttachable(JsonNode node, List<BackgroundAgent> agents) {
        String id = text(node, "id");
        if (id.isEmpty()) {
            return;
        }
        String state = text(node, "state");
        String status = state.isEmpty() ? text(node, "status") : state;
        agents.add(new BackgroundAgent(id, text(node, "name"), status, text(node, "summary")));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }
}
