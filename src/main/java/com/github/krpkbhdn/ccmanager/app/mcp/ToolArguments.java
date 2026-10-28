package com.github.krpkbhdn.ccmanager.app.mcp;

import tools.jackson.databind.JsonNode;

final class ToolArguments {

    private ToolArguments() {}

    /** @throws IllegalArgumentException when the field is missing or not a whole number */
    static long requiredId(JsonNode arguments, String field) {
        var value = arguments.path(field);
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("Argument '" + field + "' must be a numeric id");
        }
        return value.asLong();
    }
}
