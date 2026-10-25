package com.github.krpkbhdn.ccmanager.extension.plugin;

import java.util.List;

/** What {@code claude plugin details} reports; alwaysOnTokens is the estimated cost added to every session (0 if unknown). */
public record PluginDetails(String description, List<PluginComponent> components, int alwaysOnTokens) {}
