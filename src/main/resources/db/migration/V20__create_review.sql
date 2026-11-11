CREATE TABLE review (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    ccm_session_id TEXT,
    title          TEXT NOT NULL,
    note           TEXT NOT NULL DEFAULT '',
    status         TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'APPROVED')),
    created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at     TEXT
);

CREATE TABLE review_image (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    review_id INTEGER NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    label     TEXT NOT NULL DEFAULT '',
    file_name TEXT NOT NULL,
    width     INTEGER NOT NULL,
    height    INTEGER NOT NULL
);

CREATE TABLE review_mark (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    image_id INTEGER NOT NULL REFERENCES review_image (id) ON DELETE CASCADE,
    kind     TEXT NOT NULL CHECK (kind IN ('RECTANGLE', 'ELLIPSE', 'PIN')),
    x        REAL NOT NULL,
    y        REAL NOT NULL,
    w        REAL NOT NULL,
    h        REAL NOT NULL,
    comment  TEXT NOT NULL DEFAULT ''
);

CREATE INDEX idx_review_project ON review (project_id);
CREATE INDEX idx_review_image_review ON review_image (review_id);
CREATE INDEX idx_review_mark_image ON review_mark (image_id);
