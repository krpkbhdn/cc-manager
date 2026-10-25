package com.github.krpkbhdn.ccmanager.extension.plugin;

/** A plugin offered by an added marketplace; pluginId is {@code name@marketplace}. */
public record AvailablePlugin(String pluginId, String name, String description, String marketplaceName, long installCount) {}
