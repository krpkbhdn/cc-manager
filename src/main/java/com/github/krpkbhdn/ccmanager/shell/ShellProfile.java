package com.github.krpkbhdn.ccmanager.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** An installed interactive shell: what the user picks by name and how it is started. */
public record ShellProfile(String name, Path executable, List<String> arguments) {

    public ShellProfile {
        arguments = List.copyOf(arguments);
    }

    public List<String> command() {
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(arguments);
        return command;
    }
}
