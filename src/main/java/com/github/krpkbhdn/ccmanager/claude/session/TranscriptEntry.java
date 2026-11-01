package com.github.krpkbhdn.ccmanager.claude.session;

/**
 * One readable line of a subagent transcript: who said it (or which tool) and the text. {@code toolUseId} links a
 * tool call to its result and {@code uuid} is the transcript line it came from; both are empty when unknown.
 * {@code fullInput} is the uncut input of a tool call (empty otherwise), for checks that must not miss a cut-off tail.
 */
public record TranscriptEntry(Kind kind, String title, String text, String toolUseId, String uuid, String fullInput) {

    public TranscriptEntry(Kind kind, String title, String text, String toolUseId, String uuid) {
        this(kind, title, text, toolUseId, uuid, "");
    }

    public enum Kind {
        PROMPT,
        ASSISTANT,
        TOOL_CALL,
        TOOL_RESULT
    }
}
