package com.github.krpkbhdn.ccmanager.git;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Predicate;

/** Pure branch names derived from a task description. */
public final class TaskBranchNames {

    private static final String PREFIX = "task/";
    private static final String EMPTY_SLUG = "task";
    private static final int MAX_SLUG_LENGTH = 40;
    private static final int FIRST_SUFFIX = 2;
    private static final int MAX_ATTEMPTS = 100;

    private TaskBranchNames() {}

    public static String slugOf(String task) {
        var slug = trimDashes(task == null ? "" : task.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = trimDashes(slug.substring(0, MAX_SLUG_LENGTH));
        }
        return slug.isEmpty() ? EMPTY_SLUG : slug;
    }

    public static String branchFor(String task, Collection<String> existingBranches) {
        return branchFor(task, existingBranches::contains);
    }

    /**
     * The first {@code task/<slug>[-n]} that is not {@code taken}; the caller says what taken means (branches, folders).
     *
     * @throws IllegalStateException when {@value #MAX_ATTEMPTS} names are all taken
     */
    public static String branchFor(String task, Predicate<String> taken) {
        var base = PREFIX + slugOf(task);
        var candidate = base;
        for (var n = FIRST_SUFFIX; taken.test(candidate); n++) {
            if (n >= FIRST_SUFFIX + MAX_ATTEMPTS) {
                throw new IllegalStateException("No free branch name left for " + base + "; remove old task branches");
            }
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static String trimDashes(String text) {
        return text.replaceAll("^-+|-+$", "");
    }
}
