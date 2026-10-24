CREATE TABLE hook_event (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    ccm_session_id    TEXT NOT NULL,
    event             TEXT NOT NULL,
    agent_id          TEXT,
    claude_session_id TEXT,
    payload           TEXT NOT NULL,
    received_at       TEXT NOT NULL
);

CREATE INDEX idx_hook_event_session ON hook_event (ccm_session_id, id);
