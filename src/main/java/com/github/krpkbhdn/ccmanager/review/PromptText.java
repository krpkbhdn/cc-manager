package com.github.krpkbhdn.ccmanager.review;

import java.util.regex.Pattern;

/** Makes untrusted text safe for the one-line prompt typed into a terminal. */
public final class PromptText {

    /** Control and format characters (zero-width, bidi, Unicode tags), line and paragraph separators: none may reach the one-line prompt typed into the terminal. */
    private static final Pattern LINE_BREAKING_OR_HIDDEN = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}\\x{E0000}-\\x{E007F}]");

    private PromptText() {
    }

    /** Replaces every line-breaking, control or hidden character with a space. */
    public static String withoutHidden(String text) {
        return LINE_BREAKING_OR_HIDDEN.matcher(text).replaceAll(" ");
    }

    /** {@link #withoutHidden} plus collapsed and trimmed spaces. */
    public static String oneLine(String text) {
        return withoutHidden(text).replaceAll(" {2,}", " ").strip();
    }
}
