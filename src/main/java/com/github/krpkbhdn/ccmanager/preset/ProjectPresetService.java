package com.github.krpkbhdn.ccmanager.preset;

import com.github.krpkbhdn.ccmanager.config.NameRules;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Project presets in SQLite: bundled ones are seeded and kept in step with the app, user ones are plain rows.
 * Every change of the components raises the preset's revision, which tells projects that applied it that it moved on.
 */
@Service
public class ProjectPresetService {

    public static final int MAX_NAME = 60;
    public static final int MAX_DESCRIPTION = 200;

    private static final Logger log = LoggerFactory.getLogger(ProjectPresetService.class);
    private static final String SELECT = "SELECT id, name, description, bundled_key, bundled_version, edited, revision FROM project_preset WHERE deleted = 0";
    private static final String TOUCH = "UPDATE project_preset SET revision = revision + 1, edited = CASE WHEN bundled_key IS NULL THEN 0 ELSE 1 END WHERE id = ?";

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final BundledPresetLoader bundled;
    private final ChangeListeners listeners = new ChangeListeners("Project preset");

    public ProjectPresetService(JdbcClient jdbc, TransactionTemplate transaction, BundledPresetLoader bundled) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.bundled = bundled;
    }

    /** Bundled presets in the order of presets.json (General first), then the others by name; never by database id. */
    public List<ProjectPreset> list() {
        var shippedOrder = bundled.presets().stream().map(BundledPreset::key).toList();
        return jdbc.sql(SELECT).query(this::mapRow).list().stream().sorted(listOrder(shippedOrder)).toList();
    }

    static Comparator<ProjectPreset> listOrder(List<String> shippedOrder) {
        return Comparator.comparingInt((ProjectPreset preset) -> preset.bundledKey() == null || !shippedOrder.contains(preset.bundledKey()) ? shippedOrder.size() : shippedOrder.indexOf(preset.bundledKey()))
                .thenComparing(ProjectPreset::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(ProjectPreset::id);
    }

    public Optional<ProjectPreset> get(long id) {
        return jdbc.sql(SELECT + " AND id = ?").param(id).query(this::mapRow).optional();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public ProjectPreset create(String name, String description) {
        var cleanName = validate(name, description);
        var id = transaction.execute(status -> {
            requireFreeName(cleanName, -1);
            return insert(cleanName, description.strip(), null, null, List.of());
        });
        listeners.fire();
        return require(id);
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public ProjectPreset update(long id, String name, String description) {
        require(id);
        var cleanName = validate(name, description);
        transaction.executeWithoutResult(status -> {
            requireFreeName(cleanName, id);
            jdbc.sql("UPDATE project_preset SET name = ?, description = ?, edited = CASE WHEN bundled_key IS NULL THEN 0 ELSE 1 END WHERE id = ?")
                    .param(cleanName).param(description.strip()).param(id).update();
        });
        listeners.fire();
        return require(id);
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public ProjectPreset duplicate(long id, String newName) {
        var source = require(id);
        var cleanName = validate(newName, source.description());
        var copy = transaction.execute(status -> {
            requireFreeName(cleanName, -1);
            return insert(cleanName, source.description(), null, null, source.components().stream().map(c -> new PresetComponentSpec(c.kind(), c.name(), c.payload())).toList());
        });
        listeners.fire();
        return require(copy);
    }

    /** A bundled preset is only marked deleted, so it is not seeded again; a user preset is removed. */
    public void delete(long id) {
        var changed = transaction.execute(status ->
                jdbc.sql("UPDATE project_preset SET deleted = 1 WHERE id = ? AND bundled_key IS NOT NULL AND deleted = 0").param(id).update()
                        + jdbc.sql("DELETE FROM project_preset WHERE id = ? AND bundled_key IS NULL").param(id).update());
        if (changed != null && changed > 0) {
            listeners.fire();
        }
    }

    /** Resets one bundled preset to its shipped content, also bringing back a deleted one. */
    public void restore(String key) {
        var preset = bundled.presets().stream().filter(p -> p.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bundled preset: " + key));
        transaction.executeWithoutResult(status -> restore(preset));
        listeners.fire();
    }

    public void restoreAll() {
        for (var preset : bundled.presets()) {
            try {
                transaction.executeWithoutResult(status -> restore(preset));
            } catch (IllegalArgumentException e) {
                log.warn("Bundled preset {} not restored: its name is used by another preset", preset.key());
            }
        }
        listeners.fire();
    }

    /** @throws IllegalArgumentException with a user-readable message for invalid input */
    public PresetComponent addComponent(long presetId, PresetComponentKind kind, String name, JsonNode payload) {
        require(presetId);
        var spec = PresetComponentValidator.validate(kind, name, payload);
        var id = transaction.execute(status -> {
            requireFreeComponent(presetId, spec, -1);
            var created = insertComponent(presetId, spec);
            jdbc.sql(TOUCH).param(presetId).update();
            return created;
        });
        listeners.fire();
        return component(id);
    }

    /** Changes name and payload of a component; its kind stays. */
    public PresetComponent updateComponent(long componentId, String name, JsonNode payload) {
        var current = component(componentId);
        var presetId = presetOf(componentId);
        var spec = PresetComponentValidator.validate(current.kind(), name, payload);
        transaction.executeWithoutResult(status -> {
            requireFreeComponent(presetId, spec, componentId);
            jdbc.sql("UPDATE project_preset_component SET name = ?, payload = ? WHERE id = ?").param(spec.name()).param(spec.payload().toString()).param(componentId).update();
            jdbc.sql(TOUCH).param(presetId).update();
        });
        listeners.fire();
        return component(componentId);
    }

    public void removeComponent(long componentId) {
        var presetId = presetOf(componentId);
        transaction.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM project_preset_component WHERE id = ?").param(componentId).update();
            jdbc.sql(TOUCH).param(presetId).update();
        });
        listeners.fire();
    }

    /** Swaps the component with its nearest neighbour of the same kind (the UI lists one kind at a time); at the end of the list nothing happens. */
    public void moveComponent(long componentId, boolean up) {
        var presetId = presetOf(componentId);
        var moved = Boolean.TRUE.equals(transaction.execute(status -> swapWithNeighbour(presetId, componentId, up)));
        if (moved) {
            listeners.fire();
        }
    }

    /** Seeds missing bundled presets (not deleted ones) and upgrades unedited ones whose shipped version rose. */
    @PostConstruct
    public void syncBundled() {
        for (var preset : bundled.presets()) {
            try {
                transaction.executeWithoutResult(status -> sync(preset));
            } catch (RuntimeException e) {
                log.warn("Bundled preset {} skipped: {}", preset.key(), e.getClass().getSimpleName());
            }
        }
        listeners.fire();
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change made through this service */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private boolean swapWithNeighbour(long presetId, long componentId, boolean up) {
        var all = jdbc.sql("SELECT id, position FROM project_preset_component WHERE preset_id = ? "
                        + "AND kind = (SELECT kind FROM project_preset_component WHERE id = ?) ORDER BY position, id")
                .param(presetId).param(componentId).query((rs, n) -> new long[] {rs.getLong("id"), rs.getLong("position")}).list();
        var index = 0;
        while (index < all.size() && all.get(index)[0] != componentId) {
            index++;
        }
        var other = up ? index - 1 : index + 1;
        if (index >= all.size() || other < 0 || other >= all.size()) {
            return false;
        }
        jdbc.sql("UPDATE project_preset_component SET position = ? WHERE id = ?").param(all.get(other)[1]).param(componentId).update();
        jdbc.sql("UPDATE project_preset_component SET position = ? WHERE id = ?").param(all.get(index)[1]).param(all.get(other)[0]).update();
        jdbc.sql(TOUCH).param(presetId).update();
        return true;
    }

    private void sync(BundledPreset preset) {
        var row = jdbc.sql("SELECT bundled_version, edited, deleted FROM project_preset WHERE bundled_key = ?").param(preset.key())
                .query((rs, n) -> new int[] {rs.getInt("bundled_version"), rs.getInt("edited"), rs.getInt("deleted")}).optional();
        if (row.isEmpty()) {
            requireFreeName(preset.name(), -1);
            insert(preset.name(), preset.description(), preset.key(), preset.version(), preset.components());
        } else if (row.get()[2] == 0 && row.get()[1] == 0 && row.get()[0] < preset.version()) {
            restore(preset);
        }
    }

    private void restore(BundledPreset preset) {
        var existing = jdbc.sql("SELECT id FROM project_preset WHERE bundled_key = ?").param(preset.key()).query(Long.class).optional();
        var ownId = existing.orElse(-1L);
        if (nameTaken(preset.name(), ownId)) {
            throw new IllegalArgumentException("A preset named \"" + preset.name() + "\" already exists: rename it first.");
        }
        if (existing.isEmpty()) {
            insert(preset.name(), preset.description(), preset.key(), preset.version(), preset.components());
            return;
        }
        var id = existing.get();
        jdbc.sql("UPDATE project_preset SET name = ?, description = ?, bundled_version = ?, edited = 0, deleted = 0, revision = revision + 1 WHERE id = ?")
                .param(preset.name()).param(preset.description()).param(preset.version()).param(id).update();
        jdbc.sql("DELETE FROM project_preset_component WHERE preset_id = ?").param(id).update();
        preset.components().forEach(spec -> insertComponent(id, PresetComponentValidator.validate(spec.kind(), spec.name(), spec.payload())));
    }

    private long insert(String name, String description, String key, Integer version, List<PresetComponentSpec> components) {
        var id = jdbc.sql("INSERT INTO project_preset (name, description, bundled_key, bundled_version) VALUES (?, ?, ?, ?) RETURNING id")
                .param(name).param(description).param(key).param(version).query(Long.class).single();
        components.forEach(spec -> insertComponent(id, PresetComponentValidator.validate(spec.kind(), spec.name(), spec.payload())));
        return id;
    }

    private long insertComponent(long presetId, PresetComponentSpec spec) {
        return jdbc.sql("INSERT INTO project_preset_component (preset_id, position, kind, name, payload) "
                        + "VALUES (?, (SELECT COALESCE(MAX(position) + 1, 0) FROM project_preset_component WHERE preset_id = ?), ?, ?, ?) RETURNING id")
                .param(presetId).param(presetId).param(spec.kind().name()).param(spec.name()).param(spec.payload().toString()).query(Long.class).single();
    }

    private void requireFreeComponent(long presetId, PresetComponentSpec spec, long ownId) {
        var taken = jdbc.sql("SELECT COUNT(*) FROM project_preset_component WHERE preset_id = ? AND kind = ? AND name = ? AND id <> ?")
                .param(presetId).param(spec.kind().name()).param(spec.name()).param(ownId).query(Long.class).single() > 0;
        if (taken) {
            throw new IllegalArgumentException("The preset already has a " + spec.kind() + " named \"" + spec.name() + "\".");
        }
        var secondSettings = spec.kind() == PresetComponentKind.SETTINGS
                && jdbc.sql("SELECT COUNT(*) FROM project_preset_component WHERE preset_id = ? AND kind = 'SETTINGS' AND id <> ?").param(presetId).param(ownId).query(Long.class).single() > 0;
        if (secondSettings) {
            throw new IllegalArgumentException("A preset can have only one settings component.");
        }
    }

    private ProjectPreset require(long id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));
    }

    public Optional<PresetComponent> findComponent(long componentId) {
        return jdbc.sql("SELECT id, kind, name, payload FROM project_preset_component WHERE id = ?").param(componentId).query(this::mapComponent).list().stream()
                .filter(Objects::nonNull).findFirst();
    }

    private PresetComponent component(long componentId) {
        return findComponent(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
    }

    private long presetOf(long componentId) {
        return jdbc.sql("SELECT preset_id FROM project_preset_component WHERE id = ?").param(componentId).query(Long.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
    }

    private void requireFreeName(String name, long ownId) {
        if (nameTaken(name, ownId)) {
            throw new IllegalArgumentException("A preset named \"" + name + "\" already exists.");
        }
    }

    /** A deleted bundled preset does not hold its name (the column is unique, so it is renamed aside; restore puts the shipped name back). */
    private boolean nameTaken(String name, long ownId) {
        jdbc.sql("UPDATE project_preset SET name = name || ' [deleted ' || bundled_key || ']' WHERE name = ? AND deleted = 1 AND id <> ?").param(name).param(ownId).update();
        return jdbc.sql("SELECT COUNT(*) FROM project_preset WHERE name = ? AND id <> ?").param(name).param(ownId).query(Long.class).single() > 0;
    }

    private String validate(String name, String description) {
        var cleanName = NameRules.requireName(name, MAX_NAME);
        if (description == null) {
            throw new IllegalArgumentException("The description must not be missing.");
        }
        if (description.strip().length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("The description is longer than " + MAX_DESCRIPTION + " characters.");
        }
        return cleanName;
    }

    /** @return null for a row whose kind or payload cannot be read, so one corrupt component does not hide the rest */
    private PresetComponent mapComponent(ResultSet rs, int row) throws SQLException {
        var id = rs.getLong("id");
        try {
            return new PresetComponent(id, PresetComponentKind.valueOf(rs.getString("kind")), rs.getString("name"), PresetComponentValidator.MAPPER.readTree(rs.getString("payload")));
        } catch (RuntimeException e) {
            log.warn("Preset component {} skipped, it cannot be read: {}", id, e.getClass().getSimpleName());
            return null;
        }
    }

    private ProjectPreset mapRow(ResultSet rs, int row) throws SQLException {
        var id = rs.getLong("id");
        var key = rs.getString("bundled_key");
        var edited = rs.getInt("edited") != 0;
        var storedVersion = rs.getInt("bundled_version");
        var newer = key != null && bundled.presets().stream().anyMatch(p -> p.key().equals(key) && p.version() > storedVersion);
        var components = new ArrayList<>(jdbc.sql("SELECT id, kind, name, payload FROM project_preset_component WHERE preset_id = ? ORDER BY position, id")
                .param(id).query(this::mapComponent).list());
        components.removeIf(Objects::isNull);
        return new ProjectPreset(id, rs.getString("name"), rs.getString("description"), key, edited, edited && newer, rs.getInt("revision"), List.copyOf(components));
    }
}
