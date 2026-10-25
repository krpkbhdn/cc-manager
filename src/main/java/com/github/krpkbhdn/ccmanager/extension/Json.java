package com.github.krpkbhdn.ccmanager.extension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tolerant JSON reading shared by the extension parsers: unknown fields are ignored, missing ones are empty. */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    /** @throws ExtensionException when the text is not JSON */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (RuntimeException e) {
            throw new ExtensionException("Unrecognised claude output");
        }
    }

    public static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }
}
