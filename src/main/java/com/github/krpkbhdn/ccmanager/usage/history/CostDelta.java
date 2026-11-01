package com.github.krpkbhdn.ccmanager.usage.history;

import java.util.OptionalDouble;

/** Cost added since the previous report of a session; a lower cumulative cost means Claude restarted the counter. */
public final class CostDelta {

    private CostDelta() {
    }

    public static double of(OptionalDouble previous, double current) {
        return previous.isPresent() && current >= previous.getAsDouble() ? current - previous.getAsDouble() : current;
    }
}
