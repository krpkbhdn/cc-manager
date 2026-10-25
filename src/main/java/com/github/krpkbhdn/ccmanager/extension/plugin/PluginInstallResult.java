package com.github.krpkbhdn.ccmanager.extension.plugin;

/**
 * Outcome of an install. When {@code pendingCommand} is set nothing was installed: show it to the user and,
 * only if they accept, install again with its sha256.
 */
public record PluginInstallResult(boolean installed, String message, ShownCommand pendingCommand) {

    public boolean needsConfirmation() {
        return pendingCommand != null;
    }
}
