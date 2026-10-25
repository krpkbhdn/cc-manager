package com.github.krpkbhdn.ccmanager.extension.plugin;

/** An added plugin marketplace; sourceKind is github, git, url or path and reference its repo, URL or path. */
public record Marketplace(String name, String sourceKind, String reference, String installLocation) {}
