package com.github.krpkbhdn.ccmanager.web;

/** One CSS property the user edited in the page, with its value before and after. */
public record StyleChange(String property, String from, String to) {
}
