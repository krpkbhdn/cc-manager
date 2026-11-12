CREATE TABLE web_review_revision (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    number         INTEGER NOT NULL,
    ccm_session_id TEXT,
    note           TEXT NOT NULL DEFAULT '',
    status         TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'APPROVED')),
    created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at        TEXT,
    settled_at     TEXT,
    UNIQUE (project_id, number)
);

CREATE TABLE web_review_page (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    page_key   TEXT NOT NULL,
    last_url   TEXT NOT NULL,
    title      TEXT NOT NULL DEFAULT '',
    last_visited_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now')),
    UNIQUE (project_id, page_key)
);

CREATE TABLE web_review_item (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    revision_id        INTEGER NOT NULL REFERENCES web_review_revision (id) ON DELETE CASCADE,
    page_id            INTEGER NOT NULL REFERENCES web_review_page (id) ON DELETE CASCADE,
    n                  INTEGER NOT NULL,
    anchor_json        TEXT NOT NULL,
    comment            TEXT NOT NULL DEFAULT '',
    style_changes_json TEXT NOT NULL DEFAULT '[]',
    status             TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SENT', 'DONE', 'DISMISSED')),
    screenshot_file    TEXT,
    created_at         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_review_item_revision ON web_review_item (revision_id);
CREATE INDEX idx_web_review_item_page_status ON web_review_item (page_id, status);
CREATE UNIQUE INDEX idx_web_review_one_pending_revision ON web_review_revision (project_id) WHERE status = 'PENDING';
CREATE INDEX idx_web_review_revision_project_status ON web_review_revision (project_id, status);
