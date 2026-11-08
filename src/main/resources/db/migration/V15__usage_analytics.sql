CREATE TABLE usage_sample (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    at INTEGER NOT NULL,
    ccm_session_id TEXT NOT NULL,
    claude_session_id TEXT,
    project_id INTEGER NOT NULL,
    model TEXT,
    effort TEXT,
    context_percent REAL,
    cost_usd REAL NOT NULL,
    cost_delta REAL NOT NULL
);
CREATE INDEX idx_usage_sample_at ON usage_sample (at);
CREATE INDEX idx_usage_sample_claude_session ON usage_sample (claude_session_id, at);

CREATE TABLE rate_limit_sample (
    at INTEGER NOT NULL,
    five_hour_pct REAL,
    five_hour_resets_at INTEGER,
    seven_day_pct REAL,
    seven_day_resets_at INTEGER
);
CREATE INDEX idx_rate_limit_sample_at ON rate_limit_sample (at);

CREATE TABLE usage_daily (
    day TEXT NOT NULL,
    project_id INTEGER NOT NULL,
    model TEXT NOT NULL,
    cost_usd REAL NOT NULL,
    sessions INTEGER NOT NULL,
    samples INTEGER NOT NULL,
    PRIMARY KEY (day, project_id, model)
);

CREATE TABLE usage_alert (
    alert_key TEXT PRIMARY KEY,
    fired_at INTEGER NOT NULL
);
