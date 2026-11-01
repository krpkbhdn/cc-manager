package com.github.krpkbhdn.ccmanager.voice.recognize;

/** Turns whatever a native downcall throws into an unchecked exception; an {@link Error} is rethrown, so the method does not return for it. */
final class NativeFailure {

    private NativeFailure() {}

    static RuntimeException of(String what, Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(what + " failed", cause);
    }
}
