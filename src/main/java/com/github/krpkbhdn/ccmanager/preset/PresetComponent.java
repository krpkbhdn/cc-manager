package com.github.krpkbhdn.ccmanager.preset;

import tools.jackson.databind.JsonNode;

public record PresetComponent(long id, PresetComponentKind kind, String name, JsonNode payload) {
}
