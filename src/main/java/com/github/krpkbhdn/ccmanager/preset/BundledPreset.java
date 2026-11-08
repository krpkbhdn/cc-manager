package com.github.krpkbhdn.ccmanager.preset;

import java.util.List;

public record BundledPreset(String key, int version, String name, String description, List<PresetComponentSpec> components) {
}
