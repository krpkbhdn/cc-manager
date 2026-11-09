package com.github.krpkbhdn.ccmanager.run;

import com.github.krpkbhdn.ccmanager.config.NameRules;
import com.github.krpkbhdn.ccmanager.config.UniqueViolations;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Run configurations of projects, stored in SQLite; a name is unique within its project. */
@Service
public class RunConfigurationService {

    public static final int MAX_NAME = 60;
    public static final int MAX_COMMAND = 4_000;
    private static final String SELECT = "SELECT id, project_id, name, command, directory, confirmed FROM run_configuration";

    private final JdbcClient jdbc;
    private final ChangeListeners listeners = new ChangeListeners("Run configuration");

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

    public Set<Long> existingIds() {
        return Set.copyOf(jdbc.sql("SELECT id FROM run_configuration").query(Long.class).list());
    }

    /**
     * Creates the configuration or replaces command and directory of the one with this name; written by the user, so confirmed.
     *
     * @throws IllegalArgumentException with a user-readable message for invalid input
     */
    public RunConfiguration save(long projectId, String name, String command, Path directory) {
        return upsert(projectId, name, command, directory, "1", "1");
    }

    /** Like {@link #save} for a configuration written by an agent: new, or with another command or directory, it needs the user's confirmation before it runs. */
    public RunConfiguration saveByAgent(long projectId, String name, String command, Path directory) {
        return upsert(projectId, name, command, directory, "0",
                "CASE WHEN command = excluded.command AND directory = excluded.directory THEN confirmed ELSE 0 END");
    }

    /** The user agreed to run exactly this command and directory; false when the configuration changed since it was shown, so nothing is confirmed. */
    public boolean confirm(long id, String command, Path directory) {
        var updated = jdbc.sql("UPDATE run_configuration SET confirmed = 1 WHERE id = ? AND command = ? AND directory = ?")
                .param(id).param(command).param(directory.toString()).update() > 0;
        if (updated) {
            listeners.fire();
        }
        return updated;
    }

    private RunConfiguration upsert(long projectId, String name, String command, Path directory, String insertedConfirmed, String conflictConfirmed) {
        var cleanName = validate(projectId, name, command, directory);
        jdbc.sql("INSERT INTO run_configuration (project_id, name, command, directory, confirmed) VALUES (?, ?, ?, ?, " + insertedConfirmed + ") "
                        + "ON CONFLICT (project_id, name) DO UPDATE SET command = excluded.command, directory = excluded.directory, confirmed = " + conflictConfirmed)
                .param(projectId).param(cleanName).param(command).param(directory.toString()).update();
        listeners.fire();
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
            throw nameTaken(cleanName);
        }
        try {
            jdbc.sql("UPDATE run_configuration SET name = ?, command = ?, directory = ?, confirmed = 1 WHERE id = ?")
                    .param(cleanName).param(command).param(directory.toString()).param(id).update();
        } catch (DataAccessException e) {
            throw UniqueViolations.isUniqueViolation(e) ? nameTaken(cleanName) : e;
        }
        listeners.fire();
        return find(id).orElseThrow();
    }

    private static IllegalArgumentException nameTaken(String name) {
        return new IllegalArgumentException("A run configuration named \"" + name + "\" already exists in this project.");
    }

    /** @return whether a configuration with that name existed */
    public boolean delete(long projectId, String name) {
        var removed = jdbc.sql("DELETE FROM run_configuration WHERE project_id = ? AND name = ?")
                .param(projectId).param(name.strip()).update() > 0;
        if (removed) {
            listeners.fire();
        }
        return removed;
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM run_configuration WHERE id = ?").param(id).update() > 0) {
            listeners.fire();
        }
    }

    /** The configurations of every project that run in [directory] (compared normalized), e.g. to list what a worktree removal deletes. */
    public List<RunConfiguration> listByDirectory(Path directory) {
        var target = directory.toAbsolutePath().normalize();
        return jdbc.sql(SELECT + " ORDER BY name COLLATE NOCASE, id").query(RunConfigurationService::mapRow).list().stream()
                .filter(configuration -> configuration.directory().toAbsolutePath().normalize().equals(target)).toList();
    }

    /** Deletes what [listByDirectory] returns, e.g. after its worktree was removed. */
    public void deleteByDirectory(Path directory) {
        var found = listByDirectory(directory);
        found.forEach(configuration -> jdbc.sql("DELETE FROM run_configuration WHERE id = ?").param(configuration.id()).update());
        if (!found.isEmpty()) {
            listeners.fire();
        }
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change to run configurations */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private String validate(long projectId, String name, String command, Path directory) {
        var cleanName = NameRules.requireName(name, MAX_NAME);
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
                rs.getString("command"), Path.of(rs.getString("directory")), rs.getBoolean("confirmed"));
    }
}
