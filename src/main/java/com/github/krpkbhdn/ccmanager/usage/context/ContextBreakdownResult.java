package com.github.krpkbhdn.ccmanager.usage.context;

/** Exactly one of the two is set: the measured breakdown, or why it could not be measured. */
public record ContextBreakdownResult(ContextBreakdown breakdown, String failure) {

    public static ContextBreakdownResult success(ContextBreakdown breakdown) {
        return new ContextBreakdownResult(breakdown, null);
    }

    public static ContextBreakdownResult failed(String reason) {
        return new ContextBreakdownResult(null, reason);
    }

    public boolean succeeded() {
        return breakdown != null;
    }
}
