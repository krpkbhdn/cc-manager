package com.github.krpkbhdn.ccmanager.project;

import java.nio.file.Path;
import java.time.Instant;

/** A registered directory, or a group (a service directory holding member projects); groupId is null when ungrouped. */
public record Project(long id, String name, Path path, Instant createdAt, ModelSelection modelSelection,
                      boolean group, Long groupId) {

    public Project(long id, String name, Path path, Instant createdAt, ModelSelection modelSelection) {
        this(id, name, path, createdAt, modelSelection, false, null);
    }

    public Project(long id, String name, Path path, Instant createdAt) {
        this(id, name, path, createdAt, ModelSelection.DEFAULT);
    }
}
