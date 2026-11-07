CREATE TABLE transcript_file (
    id INTEGER PRIMARY KEY,
    path TEXT UNIQUE NOT NULL,
    claude_session_id TEXT NOT NULL,
    cwd TEXT NOT NULL DEFAULT '',
    indexed_bytes INTEGER NOT NULL DEFAULT 0,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    modified_at INTEGER NOT NULL DEFAULT 0,
    last_entry_at INTEGER NOT NULL DEFAULT 0,
    entry_count INTEGER NOT NULL DEFAULT 0,
    sensitive_tool_ids TEXT NOT NULL DEFAULT ''
);

CREATE VIRTUAL TABLE transcript_entry_fts USING fts5(
    text,
    role UNINDEXED,
    file_id UNINDEXED,
    entry_uuid UNINDEXED,
    line_offset UNINDEXED,
    at UNINDEXED,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TABLE transcript_entry_ref (
    entry_id INTEGER PRIMARY KEY,
    file_id INTEGER NOT NULL
);

CREATE INDEX idx_transcript_entry_ref_file ON transcript_entry_ref (file_id);
