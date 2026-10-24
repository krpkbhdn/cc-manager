package com.github.krpkbhdn.ccmanager.project;

import java.nio.file.Path;
import java.time.Instant;

public record Project(long id, String name, Path path, Instant createdAt, ModelSelection modelSelection) {

    public Project(long id, String name, Path path, Instant createdAt) {
        this(id, name, path, createdAt, ModelSelection.DEFAULT);
    }
}
