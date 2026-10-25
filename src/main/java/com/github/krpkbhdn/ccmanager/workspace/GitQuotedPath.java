package com.github.krpkbhdn.ccmanager.workspace;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Decodes git's C-style quoted paths: backslash escapes and three-digit octal bytes of the UTF-8 name. */
final class GitQuotedPath {

    private static final int OCTAL_DIGITS = 3;
    private static final int OCTAL = 8;

    private GitQuotedPath() {}

    static String unquote(String path) {
        if (path.length() < 2 || path.charAt(0) != '"' || path.charAt(path.length() - 1) != '"') {
            return path;
        }
        var bytes = new ByteArrayOutputStream();
        int i = 1;
        int end = path.length() - 1;
        while (i < end) {
            int codePoint = path.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint != '\\' || i >= end) {
                bytes.writeBytes(Character.toString(codePoint).getBytes(StandardCharsets.UTF_8));
            } else if (Character.digit(path.charAt(i), OCTAL) >= 0) {
                int value = 0;
                for (int digits = 0; digits < OCTAL_DIGITS && i < end && Character.digit(path.charAt(i), OCTAL) >= 0; digits++) {
                    value = value * OCTAL + Character.digit(path.charAt(i++), OCTAL);
                }
                bytes.write(value);
            } else {
                bytes.write(escaped(path.charAt(i++)));
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** End of the quoted token that starts at {@code start}, exclusive. */
    static int endOfQuoted(String text, int start) {
        int i = start + 1;
        while (i < text.length() && text.charAt(i) != '"') {
            i += text.charAt(i) == '\\' ? 2 : 1;
        }
        return Math.min(i + 1, text.length());
    }

    private static char escaped(char c) {
        return switch (c) {
            case 'a' -> 0x07;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'v' -> 0x0B;
            default -> c;
        };
    }
}
