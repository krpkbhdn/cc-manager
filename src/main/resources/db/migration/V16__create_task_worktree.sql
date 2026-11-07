CREATE TABLE task_worktree (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id  INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    path        TEXT NOT NULL UNIQUE,
    branch      TEXT NOT NULL,
    base_branch TEXT NOT NULL,
    task        TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_worktree_project ON task_worktree (project_id);
