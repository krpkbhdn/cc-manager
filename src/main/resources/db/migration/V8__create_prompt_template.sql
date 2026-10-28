CREATE TABLE prompt_template (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    text            TEXT NOT NULL,
    project_id      INTEGER REFERENCES project (id) ON DELETE CASCADE,
    default_team_id INTEGER REFERENCES team (id) ON DELETE SET NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
