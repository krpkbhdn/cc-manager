package com.github.krpkbhdn.ccmanager.extension;

/** A failed extension operation; the message is user-readable, {@link #output()} is the CLI's raw stderr/stdout. */
public class ExtensionException extends RuntimeException {

    private final String output;

    public ExtensionException(String message, String output) {
        super(message);
        this.output = output;
    }

    public ExtensionException(String message) {
        this(message, "");
    }

    public String output() {
        return output;
    }
}
