package com.github.krpkbhdn.ccmanager.prompt;

/** A reusable task text; {@code projectId} null means global, {@code defaultTeamId} null means no preferred team, {@code description} is a short optional blurb. */
public record PromptTemplate(long id, String name, String description, String text, Long projectId, Long defaultTeamId) {}
