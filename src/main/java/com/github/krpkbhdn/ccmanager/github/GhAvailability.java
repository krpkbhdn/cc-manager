package com.github.krpkbhdn.ccmanager.github;

public enum GhAvailability {
    MISSING,
    NOT_AUTHENTICATED,
    READY,
    /** gh runs but could not tell (offline, timeout): the previous state stays. */
    UNKNOWN
}
