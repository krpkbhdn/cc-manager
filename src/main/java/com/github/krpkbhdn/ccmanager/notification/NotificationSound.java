package com.github.krpkbhdn.ccmanager.notification;

import java.util.Optional;

/** The two earcons: ATTENTION when the user must act or something failed, SOFT when work is simply done. */
public enum NotificationSound {
    ATTENTION("/sounds/attention.wav"),
    SOFT("/sounds/finished.wav");

    private final String resource;

    NotificationSound(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }

    /** INFO is pure noise and stays silent. */
    public static Optional<NotificationSound> forKind(NotificationKind kind) {
        return switch (kind) {
            case NEEDS_ACTION, ERROR -> Optional.of(ATTENTION);
            case FINISHED -> Optional.of(SOFT);
            case INFO -> Optional.empty();
        };
    }
}
