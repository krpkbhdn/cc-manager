package com.github.krpkbhdn.ccmanager.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

/** Projects are plain directories registered by the user; stored in SQLite. */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final String SELECT = "SELECT id, name, path, created_at, model, effort FROM project";

    private final JdbcClient jdbc;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    public ProjectService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Project> list() {
        return jdbc.sql(SELECT + " ORDER BY name COLLATE NOCASE, id").query(ProjectService::mapRow).list();
    }

    public Optional<Project> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(ProjectService::mapRow).optional();
    }

    /** @throws IllegalArgumentException with a user-readable message (not a directory, already added...) */
    public Project add(Path directory) {
        var path = realDirectory(directory);
        if (findByPath(path).isPresent()) {
            throw new IllegalArgumentException("This directory is already added as a project: " + path);
        }
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES (?, ?, ?)")
                .param(nameOf(path))
                .param(path.toString())
                .param(Instant.now().toString())
                .update(keys);
        var project = find(keys.getKey().longValue()).orElseThrow();
        fireChanged();
        return project;
    }

    public Project rename(long id, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name must not be empty");
        }
        var updated = jdbc.sql("UPDATE project SET name = ? WHERE id = ?").param(name.strip()).param(id).update();
        if (updated == 0) {
            throw new IllegalArgumentException("Project not found: " + id);
        }
        fireChanged();
        return find(id).orElseThrow();
    }

    /** Stores the model and effort new sessions of the project start with. */
    public Project selectModel(long id, ModelSelection selection) {
        var updated = jdbc.sql("UPDATE project SET model = ?, effort = ? WHERE id = ?")
                .param(selection.model()).param(selection.effort()).param(id).update();
        if (updated == 0) {
            throw new IllegalArgumentException("Project not found: " + id);
        }
        fireChanged();
        return find(id).orElseThrow();
    }

    public void remove(long id) {
        if (jdbc.sql("DELETE FROM project WHERE id = ?").param(id).update() > 0) {
            fireChanged();
        }
    }

    /** @return unsubscribe handle; callback fires after any add/rename/remove */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    private Optional<Project> findByPath(Path path) {
        return jdbc.sql(SELECT + " WHERE path = ?").param(path.toString()).query(ProjectService::mapRow).optional();
    }

    private static Path realDirectory(Path directory) {
        if (!Files.exists(directory)) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        if (!Files.isReadable(directory)) {
            throw new IllegalArgumentException("Directory is not readable: " + directory);
        }
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nameOf(Path path) {
        var fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private void fireChanged() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Project change listener failed", e);
            }
        }
    }

    private static Project mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Project(rs.getLong("id"), rs.getString("name"), Path.of(rs.getString("path")),
                Instant.parse(rs.getString("created_at")), ModelSelection.stored(rs.getString("model"), rs.getString("effort")));
    }
}
