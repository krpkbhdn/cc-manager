package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/** Short human-readable text for one entry of permission_suggestions. */
final class SuggestionDescriber {

    private static final Map<String, Function<JsonNode, String>> BY_TYPE = Map.of(
            "setMode", SuggestionDescriber::describeMode,
            "addRules", SuggestionDescriber::describeRules,
            "addDirectories", suggestion -> "Allow access to " + joined(suggestion.path("directories")));

    private SuggestionDescriber() {}

    static String describe(JsonNode suggestion) {
        var type = suggestion.path("type").asString("");
        return BY_TYPE.getOrDefault(type, unknown -> "Apply " + (type.isEmpty() ? "permission update" : type)).apply(suggestion);
    }

    private static String describeMode(JsonNode suggestion) {
        var mode = suggestion.path("mode").asString("");
        return "acceptEdits".equals(mode) ? "Allow edits for this session" : "Switch to " + mode + " mode";
    }

    private static String describeRules(JsonNode suggestion) {
        var rules = new ArrayList<String>();
        suggestion.path("rules").forEach(rule -> {
            var content = rule.path("ruleContent").asString("");
            rules.add("`" + rule.path("toolName").asString("") + (content.isEmpty() ? "" : "(" + content + ")") + "`");
        });
        var verb = "deny".equals(suggestion.path("behavior").asString("")) ? "Always deny " : "Always allow ";
        return verb + String.join(", ", rules);
    }

    private static String joined(JsonNode array) {
        var items = new ArrayList<String>();
        array.forEach(item -> items.add(item.asString("")));
        return String.join(", ", items);
    }
}
