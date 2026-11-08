package com.github.krpkbhdn.ccmanager.preset;

import java.util.Locale;
import java.util.regex.Pattern;

/** Path segments that are unsafe to create in a project: Windows device names, trailing dot or space, and anything under .git. */
public final class ProjectPathRules {

    private static final Pattern DEVICE = Pattern.compile("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");

    private ProjectPathRules() {
    }

    /** @throws IllegalArgumentException when the segment cannot be a safe file or folder name */
    public static void requireSafeSegment(String segment, String fullPath) {
        var dot = segment.indexOf('.');
        var base = (dot < 0 ? segment : segment.substring(0, dot)).stripTrailing().toUpperCase(Locale.ROOT);
        if (DEVICE.matcher(base).matches()) {
            throw new IllegalArgumentException("A reserved device name cannot be used in a path: " + fullPath);
        }
        if (segment.endsWith(".") || segment.endsWith(" ")) {
            throw new IllegalArgumentException("A path segment must not end with a dot or a space: " + fullPath);
        }
        if (segment.equalsIgnoreCase(".git")) {
            throw new IllegalArgumentException("Nothing under .git can be written: " + fullPath);
        }
    }
}
