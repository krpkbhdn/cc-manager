package com.github.krpkbhdn.ccmanager.update;

import java.util.Optional;
import java.util.regex.Pattern;

/** MAJOR.MINOR.PATCH, read from a release tag such as {@code v1.0.59}. */
record ReleaseVersion(int major, int minor, int patch) implements Comparable<ReleaseVersion> {

    private static final Pattern FORMAT = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)");

    static Optional<ReleaseVersion> parse(String text) {
        var matcher = FORMAT.matcher(text.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ReleaseVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        var byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        var byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
