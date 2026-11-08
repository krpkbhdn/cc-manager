package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/** A bundled skill: its profile {@code directory} and the {@code files} inside it, relative to that directory. */
public record ProfileSkill(String name, String directory, List<String> files) {

    public ProfileSkill {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
