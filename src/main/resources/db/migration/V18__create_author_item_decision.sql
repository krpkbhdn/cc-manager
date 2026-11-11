CREATE TABLE author_item_decision (
    item_id    TEXT NOT NULL PRIMARY KEY,
    status     TEXT NOT NULL CHECK (status IN ('INSTALLED', 'DECLINED')),
    version    INTEGER NOT NULL,
    decided_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO author_item_decision (item_id, status, version, decided_at)
SELECT item.id, d.status, 1, d.decided_at
FROM author_decision d
JOIN (
    SELECT 'USER_SETUP' AS component, 'marketplace.claude-plugins-official' AS id UNION ALL
    SELECT 'USER_SETUP', 'marketplace.ponytail' UNION ALL
    SELECT 'USER_SETUP', 'plugin.frontend-design' UNION ALL
    SELECT 'USER_SETUP', 'plugin.context7' UNION ALL
    SELECT 'USER_SETUP', 'plugin.playwright' UNION ALL
    SELECT 'USER_SETUP', 'plugin.claude-md-management' UNION ALL
    SELECT 'USER_SETUP', 'plugin.code-review' UNION ALL
    SELECT 'USER_SETUP', 'plugin.code-simplifier' UNION ALL
    SELECT 'USER_SETUP', 'plugin.feature-dev' UNION ALL
    SELECT 'USER_SETUP', 'plugin.postman' UNION ALL
    SELECT 'USER_SETUP', 'plugin.ponytail' UNION ALL
    SELECT 'USER_SETUP', 'skill.jscpd' UNION ALL
    SELECT 'USER_SETUP', 'skill.dry-refactoring' UNION ALL
    SELECT 'USER_SETUP', 'user.settings.attribution'
) item ON item.component = d.component;

INSERT INTO author_item_decision (item_id, status, version, decided_at)
SELECT 'team.dev-team', status, CASE WHEN version >= 3 THEN 3 ELSE 0 END, decided_at
FROM author_decision WHERE component = 'TEAMS';

DROP TRIGGER author_decision_project_removed;
DROP TABLE author_decision;
