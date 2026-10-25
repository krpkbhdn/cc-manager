package com.github.krpkbhdn.ccmanager.claude.session;

/** One readable line of a subagent transcript: who said it (or which tool) and the text. */
public record TranscriptEntry(Kind kind, String title, String text) {

    public enum Kind {
        PROMPT,
        ASSISTANT,
        TOOL_CALL,
        TOOL_RESULT
    }
}
