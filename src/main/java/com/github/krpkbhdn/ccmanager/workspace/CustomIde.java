package com.github.krpkbhdn.ccmanager.workspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A user-defined IDE: an executable and optional whitespace-separated arguments placed before the directory. */
public record CustomIde(long id, String name, String executable, String arguments) {

    public String ideId() {
        return CustomIdeService.ID_PREFIX + id;
    }

    /** The command prefix: no shell is involved, every argument is passed as is. */
    public List<String> command() {
        var command = new ArrayList<String>();
        command.add(executable);
        if (!arguments.isBlank()) {
            command.addAll(Arrays.asList(arguments.strip().split("\\s+")));
        }
        return List.copyOf(command);
    }
}
