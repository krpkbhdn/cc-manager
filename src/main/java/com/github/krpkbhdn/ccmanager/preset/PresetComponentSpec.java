package com.github.krpkbhdn.ccmanager.preset;

import tools.jackson.databind.JsonNode;

/** A component before it is stored: kind, name and its JSON payload (shape per kind in {@link PresetComponentValidator}). */
public record PresetComponentSpec(PresetComponentKind kind, String name, JsonNode payload) {
}
