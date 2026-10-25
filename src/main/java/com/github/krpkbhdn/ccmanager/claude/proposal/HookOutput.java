package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.util.Collection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Builds the hookSpecificOutput bodies Claude Code accepts from a PermissionRequest or Elicitation hook. */
final class HookOutput {

    static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String HOOK_SPECIFIC_OUTPUT = "hookSpecificOutput";

    private HookOutput() {}

    /** Allows the call; updatedInput replaces the tool input when given, updatedPermissions are applied when not empty. */
    static String allow(JsonNode updatedInput, Collection<? extends JsonNode> updatedPermissions) {
        var decision = MAPPER.createObjectNode().put("behavior", "allow");
        if (updatedInput != null) {
            decision.set("updatedInput", updatedInput);
        }
        if (!updatedPermissions.isEmpty()) {
            updatedPermissions.forEach(decision.putArray("updatedPermissions")::add);
        }
        return permissionRequest(decision);
    }

    /** The permission update that auto-accepts edits for the rest of the session. */
    static ObjectNode acceptEditsForSession() {
        return MAPPER.createObjectNode().put("type", "setMode").put("mode", "acceptEdits").put("destination", "session");
    }

    static String deny(String message) {
        return permissionRequest(MAPPER.createObjectNode().put("behavior", "deny").put("message", message));
    }

    static String elicitation(String action, JsonNode content) {
        var output = MAPPER.createObjectNode().put("hookEventName", "Elicitation").put("action", action);
        if (content != null) {
            output.set("content", content);
        }
        return MAPPER.createObjectNode().<ObjectNode>set(HOOK_SPECIFIC_OUTPUT, output).toString();
    }

    private static String permissionRequest(ObjectNode decision) {
        var output = MAPPER.createObjectNode().put("hookEventName", "PermissionRequest");
        output.set("decision", decision);
        return MAPPER.createObjectNode().<ObjectNode>set(HOOK_SPECIFIC_OUTPUT, output).toString();
    }
}
