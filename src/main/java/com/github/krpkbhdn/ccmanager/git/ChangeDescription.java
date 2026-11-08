package com.github.krpkbhdn.ccmanager.git;

/** A title (one line) and a markdown body describing a set of changes, for a pull request or a commit. */
public record ChangeDescription(String title, String body) {}
