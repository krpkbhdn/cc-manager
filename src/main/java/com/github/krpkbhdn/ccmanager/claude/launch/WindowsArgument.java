package com.github.krpkbhdn.ccmanager.claude.launch;

/**
 * pty4j joins arguments into a Windows command line without escaping quotes, which breaks JSON and multi-line prompts.
 * An argument that starts with a quote is passed through untouched, so it is quoted here by the CommandLineToArgvW rules.
 */
public final class WindowsArgument {

    private WindowsArgument() {}

    public static String quote(String argument) {
        if (!argument.isEmpty() && argument.chars().noneMatch(c -> c == '"' || c == ' ' || c == '\t' || c == '\n' || c == '\r')) {
            return argument;
        }
        var quoted = new StringBuilder("\"");
        int backslashes = 0;
        for (char c : argument.toCharArray()) {
            if (c == '\\') {
                backslashes++;
                continue;
            }
            quoted.append("\\".repeat(c == '"' ? backslashes * 2 + 1 : backslashes)).append(c);
            backslashes = 0;
        }
        return quoted.append("\\".repeat(backslashes * 2)).append('"').toString();
    }
}
