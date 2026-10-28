package com.github.krpkbhdn.ccmanager.prompt;

/** Values for the placeholders of a template; a null value leaves its placeholder untouched. */
public record PlaceholderValues(String project, String branch, String file) {}
