package com.github.krpkbhdn.ccmanager.usage;

/** Context window figures from the statusLine payload; current-usage tokens are null before the first response and after /compact. */
public record ContextWindow(Long windowSize, Long inputTokens, Long cacheCreationTokens, Long cacheReadTokens,
        Long outputTokens, Long totalInputTokens, Long totalOutputTokens) {

    public Long usedTokens() {
        if (inputTokens == null && cacheCreationTokens == null && cacheReadTokens == null) {
            return null;
        }
        return orZero(inputTokens) + orZero(cacheCreationTokens) + orZero(cacheReadTokens);
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
