package com.github.krpkbhdn.ccmanager.extension.mcp;

/** Hides the secrets a server URL can carry: credentials before the host and the values of query parameters. */
final class McpTarget {

    private static final String MASK = "***";

    private McpTarget() {}

    static String masked(String target) {
        return target.replaceAll("(?<=//)[^/@\s]+@", MASK + "@").replaceAll("([?&][^=&#\s]+)=[^&#\s]*", "$1=" + MASK);
    }
}
