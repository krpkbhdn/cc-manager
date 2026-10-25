package com.github.krpkbhdn.ccmanager.extension.plugin;

/** A command a marketplace runs to install a plugin; sha256 identifies exactly this command for the confirmation. */
public record ShownCommand(String command, String sha256) {}
