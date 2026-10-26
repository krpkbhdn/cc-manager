package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/** A bundled skill; {@code files} are relative to its directory in the profile. */
public record ProfileSkill(String name, List<String> files) {

    public ProfileSkill {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
