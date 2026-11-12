package com.github.krpkbhdn.ccmanager.web;

import java.util.Map;

/** Where an item sits in the page: selectors, geometry in document coordinates and what the page said about the element. */
public record ElementAnchor(String selector, String xpath, String tag, SourceHint sourceHint, Rect rect, String frame, String text,
        String outerHtml, Map<String, String> computed) {

    public ElementAnchor {
        computed = computed == null ? Map.of() : Map.copyOf(computed);
    }

    /** Component, file and line from the framework's dev metadata; any part may be null. */
    public record SourceHint(String component, String file, Integer line) {
    }

    public record Rect(double x, double y, double w, double h) {
    }
}
