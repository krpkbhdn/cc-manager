package com.github.krpkbhdn.ccmanager.author;

/** A stored decision; {@code projectId} is 0 for components that are not per project. */
public record AuthorDecision(AuthorComponent component, long projectId, AuthorChoice choice, int version) {}
