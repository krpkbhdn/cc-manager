package com.github.krpkbhdn.ccmanager.voice.recognize;

/** Recognised speech; {@code text} is empty when nothing was said. */
public record Transcript(String text, String language) {}
