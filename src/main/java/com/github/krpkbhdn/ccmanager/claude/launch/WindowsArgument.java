package com.github.krpkbhdn.ccmanager.claude.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * pty4j joins arguments into a Windows command line without escaping quotes, which breaks JSON and multi-line prompts.
 * An argument that starts with a quote is passed through untouched, so it is quoted here by the CommandLineToArgvW rules.
 */
public final class WindowsArgument {

    private static final String CMD_METACHARACTERS = "&|<>^%\r\n";

    private static final int MAX_COMMAND_LINE = 32_767;

    private WindowsArgument() {}

    /**
     * A directory as one argument of the launcher. The JDK leaves a path with a bare "&" unquoted for a .cmd or .bat
     * file and cmd.exe would split it there, so it is quoted here; the JDK passes a quoted argument through as is.
     * A trailing backslash (a drive root) is doubled so it does not escape the closing quote.
     */
    public static String directoryArgument(String launcher, Path directory) {
        var text = directory.toString();
        return isBatchFile(launcher) ? "\"" + text + (text.endsWith("\\") ? "\\" : "") + "\"" : text;
    }

    private static boolean isBatchFile(String launcher) {
        var name = launcher.toLowerCase(Locale.ROOT);
        return name.endsWith(".cmd") || name.endsWith(".bat");
    }

    /** cmd.exe runs a .cmd or .bat shim and ignores the quoting below, so its metacharacters would split the command line. */
    public static void requireSafeForBatchFile(List<String> command) {
        if (!isBatchFile(command.getFirst())) {
            return;
        }
        if (command.stream().skip(1).anyMatch(argument -> argument.chars().anyMatch(c -> CMD_METACHARACTERS.indexOf(c) >= 0))) {
            throw new IllegalStateException("Claude is installed as a .cmd shim, which cannot take the characters & | < > ^ % or line breaks. "
                    + "Install the native claude.exe (irm https://claude.ai/install.ps1 | iex) to run this.");
        }
    }

    /** @throws IllegalStateException when CreateProcess would reject the joined command line as too long */
    public static void requireFitsCommandLine(String[] quotedArguments) {
        var length = String.join(" ", quotedArguments).length();
        if (length >= MAX_COMMAND_LINE) {
            throw new IllegalStateException("The team prompts and the task make a command line of " + length + " characters; Windows allows "
                    + (MAX_COMMAND_LINE - 1) + ". Shorten the task or the role prompts.");
        }
    }

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
