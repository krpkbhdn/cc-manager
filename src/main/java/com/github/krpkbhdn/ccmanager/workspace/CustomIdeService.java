package com.github.krpkbhdn.ccmanager.workspace;

import com.github.krpkbhdn.ccmanager.config.NameRules;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** User-defined IDEs in SQLite. */
@Service
public class CustomIdeService {

    public static final String ID_PREFIX = "custom-";
    public static final int MAX_NAME = 60;
    public static final int MAX_ARGUMENTS = 500;
    private static final String SELECT = "SELECT id, name, executable, arguments FROM custom_ide";

    private final JdbcClient jdbc;
    private final ChangeListeners listeners = new ChangeListeners("Custom IDE");

    public CustomIdeService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CustomIde> list() {
        return jdbc.sql(SELECT + " ORDER BY name COLLATE NOCASE, id").query(CustomIdeService::mapRow).list();
    }

    public Optional<CustomIde> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(CustomIdeService::mapRow).optional();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public CustomIde create(String name, String executable, String arguments) {
        var cleanName = validate(null, name, executable, arguments);
        var id = jdbc.sql("INSERT INTO custom_ide (name, executable, arguments) VALUES (?, ?, ?) RETURNING id")
                .param(cleanName).param(executable.strip()).param(arguments.strip()).query(Long.class).single();
        listeners.fire();
        return find(id).orElseThrow();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public CustomIde update(long id, String name, String executable, String arguments) {
        find(id).orElseThrow(() -> new IllegalArgumentException("IDE not found: " + id));
        var cleanName = validate(id, name, executable, arguments);
        jdbc.sql("UPDATE custom_ide SET name = ?, executable = ?, arguments = ? WHERE id = ?")
                .param(cleanName).param(executable.strip()).param(arguments.strip()).param(id).update();
        listeners.fire();
        return find(id).orElseThrow();
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM custom_ide WHERE id = ?").param(id).update() > 0) {
            listeners.fire();
        }
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change made through this service */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private String validate(Long ownId, String name, String executable, String arguments) {
        var cleanName = NameRules.requireName(name, MAX_NAME);
        var taken = jdbc.sql("SELECT COUNT(*) FROM custom_ide WHERE name = ? COLLATE NOCASE AND id <> ?")
                .param(cleanName).param(ownId == null ? -1L : ownId).query(Long.class).single() > 0;
        if (taken) {
            throw new IllegalArgumentException("An IDE named \"" + cleanName + "\" already exists.");
        }
        if (!isExistingFile(executable)) {
            throw new IllegalArgumentException("The executable does not exist or is not a file.");
        }
        if (arguments == null || arguments.strip().length() > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("The arguments are longer than " + MAX_ARGUMENTS + " characters.");
        }
        return cleanName;
    }

    private static boolean isExistingFile(String executable) {
        try {
            return executable != null && !executable.isBlank() && Files.isRegularFile(Path.of(executable.strip()));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static CustomIde mapRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new CustomIde(rs.getLong("id"), rs.getString("name"), rs.getString("executable"), rs.getString("arguments"));
    }
}
