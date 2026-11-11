package com.github.krpkbhdn.ccmanager.config;

/** The name rule shared by presets, prompt templates and run configurations. */
public final class NameRules {

    private NameRules() {
    }

    /** @return the stripped name; @throws IllegalArgumentException with a user-readable message when it is empty or longer than {@code max} */
    public static String requireName(String name, int max) {
        var cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("The name must not be empty.");
        }
        if (cleanName.length() > max) {
            throw new IllegalArgumentException("The name is longer than " + max + " characters.");
        }
        return cleanName;
    }
}
