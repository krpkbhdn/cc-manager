package com.github.krpkbhdn.ccmanager.prompt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Prompt templates in SQLite, global or bound to one project, optionally with a preferred team. */
@Service
public class PromptTemplateService {

    public static final int MAX_NAME = 60;
    public static final int MAX_DESCRIPTION = 200;
    public static final int MAX_TEXT = 20_000;
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final String SELECT = "SELECT id, name, description, text, project_id, default_team_id FROM prompt_template";
    private static final String ORDER = " ORDER BY name COLLATE NOCASE, id";

    private final JdbcClient jdbc;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public PromptTemplateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PromptTemplate> list() {
        return jdbc.sql(SELECT + ORDER).query(PromptTemplateService::mapRow).list();
    }

    /** Global templates plus the ones bound to {@code projectId}. */
    public List<PromptTemplate> listFor(long projectId) {
        return jdbc.sql(SELECT + " WHERE project_id IS NULL OR project_id = ?" + ORDER).param(projectId)
                .query(PromptTemplateService::mapRow).list();
    }

    public Optional<PromptTemplate> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(PromptTemplateService::mapRow).optional();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public PromptTemplate create(String name, String description, String text, Long projectId, Long defaultTeamId) {
        var cleanName = validate(name, description, text, projectId, defaultTeamId);
        var id = jdbc.sql("INSERT INTO prompt_template (name, description, text, project_id, default_team_id) VALUES (?, ?, ?, ?, ?) RETURNING id")
                .param(cleanName).param(description.strip()).param(text).param(projectId).param(defaultTeamId).query(Long.class).single();
        fireChanged();
        return find(id).orElseThrow();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public PromptTemplate update(long id, String name, String description, String text, Long projectId, Long defaultTeamId) {
        find(id).orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        var cleanName = validate(name, description, text, projectId, defaultTeamId);
        jdbc.sql("UPDATE prompt_template SET name = ?, description = ?, text = ?, project_id = ?, default_team_id = ? WHERE id = ?")
                .param(cleanName).param(description.strip()).param(text).param(projectId).param(defaultTeamId).param(id).update();
        fireChanged();
        return find(id).orElseThrow();
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM prompt_template WHERE id = ?").param(id).update() > 0) {
            fireChanged();
        }
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change made through this service */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    private String validate(String name, String description, String text, Long projectId, Long defaultTeamId) {
        var cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("The name must not be empty.");
        }
        if (cleanName.length() > MAX_NAME) {
            throw new IllegalArgumentException("The name is longer than " + MAX_NAME + " characters.");
        }
        if (description.strip().length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("The description is longer than " + MAX_DESCRIPTION + " characters.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The text must not be empty.");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("The text is longer than " + MAX_TEXT + " characters.");
        }
        if (projectId != null && !exists("project", projectId)) {
            throw new IllegalArgumentException("The project no longer exists.");
        }
        if (defaultTeamId != null && !exists("team", defaultTeamId)) {
            throw new IllegalArgumentException("The team no longer exists.");
        }
        return cleanName;
    }

    private boolean exists(String table, long id) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE id = ?").param(id).query(Long.class).single() > 0;
    }

    private static PromptTemplate mapRow(ResultSet rs, int row) throws SQLException {
        return new PromptTemplate(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("text"),
                nullableLong(rs, "project_id"), nullableLong(rs, "default_team_id"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void fireChanged() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Prompt template listener failed", e);
            }
        }
    }
}
