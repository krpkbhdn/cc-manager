package com.github.krpkbhdn.ccmanager.preset;

import java.util.List;

/** @param defaultChanged an edited bundled preset whose shipped version is newer than the one it was copied from */
public record ProjectPreset(long id, String name, String description, String bundledKey, boolean edited, boolean defaultChanged, int revision, List<PresetComponent> components) {
}
