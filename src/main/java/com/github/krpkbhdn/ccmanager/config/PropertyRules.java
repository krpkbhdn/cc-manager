package com.github.krpkbhdn.ccmanager.config;

import java.time.Duration;

/** Startup checks for tunables: a bad value fails with the property name and the value instead of breaking a scheduler later. */
public final class PropertyRules {

    private PropertyRules() {}

    public static void positive(String property, long value) {
        require(value > 0, property, value, "must be positive");
    }

    public static void nonNegative(String property, long value) {
        require(value >= 0, property, value, "must not be negative");
    }

    public static void nonNegative(String property, double value) {
        require(value >= 0, property, value, "must not be negative");
    }

    public static void positive(String property, Duration value) {
        require(!value.isNegative() && !value.isZero(), property, value, "must be positive");
    }

    public static void nonNegative(String property, Duration value) {
        require(!value.isNegative(), property, value, "must not be negative");
    }

    public static void inRange(String property, double value, double min, double max) {
        require(value >= min && value <= max, property, value, "must be between " + min + " and " + max);
    }

    private static void require(boolean valid, String property, Object value, String rule) {
        if (!valid) {
            throw new IllegalArgumentException(property + " " + rule + ", got: " + value);
        }
    }
}
