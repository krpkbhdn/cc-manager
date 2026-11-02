package com.github.krpkbhdn.ccmanager.app.mcp;

import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class ToolArguments {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,18}");

    private ToolArguments() {}

    /** Models often send ids as strings, so a text holding a whole number is accepted too. @throws IllegalArgumentException when the field is missing or not a whole number */
    static long requiredId(JsonNode arguments, String field) {
        var value = arguments.path(field);
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isString() && WHOLE_NUMBER.matcher(value.asString().strip()).matches()) {
            return Long.parseLong(value.asString().strip());
        }
        throw new IllegalArgumentException("Argument '" + field + "' must be a numeric id");
    }
}
