package com.github.krpkbhdn.ccmanager.author;

/** A stored decision about one setup item. */
public record AuthorDecision(String itemId, AuthorChoice choice, int version) {}
