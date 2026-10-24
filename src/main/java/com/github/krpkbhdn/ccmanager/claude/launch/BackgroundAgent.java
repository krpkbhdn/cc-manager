package com.github.krpkbhdn.ccmanager.claude.launch;

/** A session listed by claude agents --json (background agents started with --bg). */
public record BackgroundAgent(String id, String name, String status, String summary) {}
