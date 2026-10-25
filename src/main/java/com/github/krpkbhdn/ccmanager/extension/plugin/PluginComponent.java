package com.github.krpkbhdn.ccmanager.extension.plugin;

import java.util.List;

/** A kind of component a plugin ships (Skills, Agents, Hooks, MCP servers, LSP servers) with its count and names. */
public record PluginComponent(String kind, int count, List<String> names) {}
