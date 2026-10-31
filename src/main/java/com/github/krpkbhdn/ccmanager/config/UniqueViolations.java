package com.github.krpkbhdn.ccmanager.config;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

/** sqlite-jdbc reports UNIQUE failures with no SQL state, so Spring does not translate them to DuplicateKeyException. */
public final class UniqueViolations {

    private static final String SQLITE_UNIQUE_MESSAGE = "UNIQUE constraint failed";

    private UniqueViolations() {
    }

    public static boolean isUniqueViolation(DataAccessException e) {
        return e instanceof DuplicateKeyException
                || e.getMostSpecificCause().getMessage() != null && e.getMostSpecificCause().getMessage().contains(SQLITE_UNIQUE_MESSAGE);
    }
}
