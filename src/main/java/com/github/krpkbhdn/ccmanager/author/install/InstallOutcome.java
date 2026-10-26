package com.github.krpkbhdn.ccmanager.author.install;

import java.util.ArrayList;
import java.util.List;

/** What a deterministic install did: names installed, already there, needing the user, failed (with reason) and MCP servers to sign in. */
public record InstallOutcome(List<String> installed, List<String> alreadyPresent, List<String> needsAttention, List<String> failed,
        List<String> signInNeeded) {

    public static final InstallOutcome NONE = new InstallOutcome(List.of(), List.of(), List.of(), List.of(), List.of());

    public InstallOutcome {
        installed = List.copyOf(installed);
        alreadyPresent = List.copyOf(alreadyPresent);
        needsAttention = List.copyOf(needsAttention);
        failed = List.copyOf(failed);
        signInNeeded = List.copyOf(signInNeeded);
    }

    public InstallOutcome merge(InstallOutcome other) {
        return new InstallOutcome(concat(installed, other.installed), concat(alreadyPresent, other.alreadyPresent),
                concat(needsAttention, other.needsAttention), concat(failed, other.failed), concat(signInNeeded, other.signInNeeded));
    }

    public boolean hasFailures() {
        return !failed.isEmpty();
    }

    /** Human-readable lines, empty when nothing happened. */
    public List<String> report() {
        var lines = new ArrayList<String>();
        installed.forEach(item -> lines.add("Installed " + item));
        alreadyPresent.forEach(item -> lines.add("Already present " + item));
        needsAttention.forEach(item -> lines.add("Needs your confirmation on the Plugins page: " + item));
        failed.forEach(item -> lines.add("Failed " + item));
        signInNeeded.forEach(item -> lines.add("Sign-in needed for MCP server " + item));
        return lines;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        var all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
