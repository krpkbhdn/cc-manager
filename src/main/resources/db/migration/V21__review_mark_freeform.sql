CREATE TABLE review_mark_new (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    image_id INTEGER NOT NULL REFERENCES review_image (id) ON DELETE CASCADE,
    kind     TEXT NOT NULL CHECK (kind IN ('RECTANGLE', 'ELLIPSE', 'PIN', 'FREEFORM')),
    x        REAL NOT NULL,
    y        REAL NOT NULL,
    w        REAL NOT NULL,
    h        REAL NOT NULL,
    comment  TEXT NOT NULL DEFAULT '',
    points   TEXT NOT NULL DEFAULT '[]'
);

INSERT INTO review_mark_new (id, image_id, kind, x, y, w, h, comment)
SELECT id, image_id, kind, x, y, w, h, comment FROM review_mark;

DROP TABLE review_mark;
ALTER TABLE review_mark_new RENAME TO review_mark;
CREATE INDEX idx_review_mark_image ON review_mark (image_id);
