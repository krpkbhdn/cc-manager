CREATE TABLE custom_ide (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL UNIQUE COLLATE NOCASE,
    executable TEXT NOT NULL,
    arguments  TEXT NOT NULL DEFAULT ''
);
