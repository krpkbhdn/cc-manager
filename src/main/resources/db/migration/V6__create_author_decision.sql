CREATE TABLE author_decision (
    component  TEXT NOT NULL,
    project_id INTEGER NOT NULL DEFAULT 0,
    status     TEXT NOT NULL CHECK (status IN ('INSTALLED', 'DECLINED')),
    version    INTEGER NOT NULL,
    decided_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (component, project_id)
);

CREATE TRIGGER author_decision_project_removed AFTER DELETE ON project
BEGIN
    DELETE FROM author_decision WHERE project_id = OLD.id;
END;

INSERT INTO author_decision (component, project_id, status, version)
SELECT 'TEAMS', 0, 'INSTALLED', 1 WHERE EXISTS (SELECT 1 FROM team WHERE name = 'Dev team');
