package com.github.krpkbhdn.ccmanager.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Run configurations of projects, stored in SQLite; a name is unique within its project. */
@Service
public class RunConfigurationService {

    public static final int MAX_NAME = 60;
    public static final int MAX_COMMAND = 4_000;
    private static final Logger log = LoggerFactory.getLogger(RunConfigurationService.class);
    private static final String SELECT = "SELECT id, project_id, name, command, directory FROM run_configuration";

    private final JdbcClient jdbc;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public RunConfigurationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RunConfiguration> list(long projectId) {
        return jdbc.sql(SELECT + " WHERE project_id = ? ORDER BY name COLLATE NOCASE, id").param(projectId)
                .query(RunConfigurationService::mapRow).list();
    }

    public Optional<RunConfiguration> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(RunConfigurationService::mapRow).optional();
    }

    /**
     * Creates the configuration or replaces command and directory of the one with this name.
     *
     * @throws IllegalArgumentException with a user-readable message for invalid input
     */
    public RunConfiguration save(long projectId, String name, String command, Path directory) {
        var cleanName = validate(projectId, name, command, directory);
        jdbc.sql("INSERT INTO run_configuration (project_id, name, command, directory) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (project_id, name) DO UPDATE SET command = excluded.command, directory = excluded.directory")
                .param(projectId).param(cleanName).param(command).param(directory.toString()).update();
        fireChanged();
        return jdbc.sql(SELECT + " WHERE project_id = ? AND name = ?").param(projectId).param(cleanName)
                .query(RunConfigurationService::mapRow).single();
    }

    /** Edit from the UI; the name may change but not onto another configuration of the project. */
    public RunConfiguration update(long id, String name, String command, Path directory) {
        var existing = find(id).orElseThrow(() -> new IllegalArgumentException("Run configuration not found: " + id));
        var cleanName = validate(existing.projectId(), name, command, directory);
        var clashes = jdbc.sql("SELECT COUNT(*) FROM run_configuration WHERE project_id = ? AND name = ? AND id <> ?")
                .param(existing.projectId()).param(cleanName).param(id).query(Long.class).single();
        if (clashes > 0) {
            throw new IllegalArgumentException("A run configuration named \"" + cleanName + "\" already exists in this project.");
        }
        jdbc.sql("UPDATE run_configuration SET name = ?, command = ?, directory = ? WHERE id = ?")
                .param(cleanName).param(command).param(directory.toString()).param(id).update();
        fireChanged();
        return find(id).orElseThrow();
    }

    /** @return whether a configuration with that name existed */
    public boolean delete(long projectId, String name) {
        var removed = jdbc.sql("DELETE FROM run_configuration WHERE project_id = ? AND name = ?")
                .param(projectId).param(name.strip()).update() > 0;
        if (removed) {
            fireChanged();
        }
        return removed;
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM run_configuration WHERE id = ?").param(id).update() > 0) {
            fireChanged();
        }
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change to run configurations */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    private String validate(long projectId, String name, String command, Path directory) {
        var cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("The name must not be empty.");
        }
        if (cleanName.length() > MAX_NAME) {
            throw new IllegalArgumentException("The name is longer than " + MAX_NAME + " characters.");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("The command must not be empty.");
        }
        if (command.length() > MAX_COMMAND) {
            throw new IllegalArgumentException("The command is longer than " + MAX_COMMAND + " characters.");
        }
        if (directory == null || !directory.isAbsolute()) {
            throw new IllegalArgumentException("The directory must be an absolute path.");
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("The directory does not exist: " + directory);
        }
        var projects = jdbc.sql("SELECT COUNT(*) FROM project WHERE id = ?").param(projectId).query(Long.class).single();
        if (projects == 0) {
            throw new IllegalArgumentException("The project no longer exists.");
        }
        return cleanName;
    }

    private static RunConfiguration mapRow(ResultSet rs, int row) throws SQLException {
        return new RunConfiguration(rs.getLong("id"), rs.getLong("project_id"), rs.getString("name"),
                rs.getString("command"), Path.of(rs.getString("directory")));
    }

    private void fireChanged() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Run configuration listener failed", e);
            }
        }
    }
}
