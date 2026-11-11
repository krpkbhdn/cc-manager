CREATE TABLE project_preset (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL UNIQUE,
    description     TEXT    NOT NULL,
    bundled_key     TEXT    UNIQUE,
    bundled_version INTEGER,
    edited          INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0,
    revision        INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE project_preset_component (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    preset_id INTEGER NOT NULL REFERENCES project_preset (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    kind      TEXT    NOT NULL CHECK (kind IN ('MARKETPLACE', 'PLUGIN', 'SKILL', 'AGENT', 'MCP_SERVER', 'INSTRUCTION', 'SETTINGS', 'FILE')),
    name      TEXT    NOT NULL,
    payload   TEXT    NOT NULL,
    UNIQUE (preset_id, kind, name)
);

CREATE TABLE project_preset_application (
    project_id INTEGER NOT NULL,
    preset_id  INTEGER NOT NULL REFERENCES project_preset (id) ON DELETE CASCADE,
    revision   INTEGER NOT NULL,
    applied_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, preset_id)
);

CREATE TRIGGER project_preset_application_project_removed AFTER DELETE ON project
BEGIN
    DELETE FROM project_preset_application WHERE project_id = OLD.id;
END;

CREATE TABLE project_preset_offer_declined (
    project_id INTEGER PRIMARY KEY
);

CREATE TRIGGER project_preset_offer_declined_project_removed AFTER DELETE ON project
BEGIN
    DELETE FROM project_preset_offer_declined WHERE project_id = OLD.id;
END;
