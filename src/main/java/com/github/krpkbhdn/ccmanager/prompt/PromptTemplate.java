package com.github.krpkbhdn.ccmanager.prompt;

/** A reusable task text; {@code projectId} null means global, {@code defaultTeamId} null means no preferred team. */
public record PromptTemplate(long id, String name, String text, Long projectId, Long defaultTeamId) {}
