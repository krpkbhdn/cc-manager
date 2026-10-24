CREATE TABLE team (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL,
    lead_role_id INTEGER,
    created_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE team_role (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    team_id     INTEGER NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT NOT NULL,
    prompt      TEXT NOT NULL,
    model       TEXT,
    effort      TEXT,
    tools       TEXT,
    is_lead     INTEGER NOT NULL DEFAULT 0,
    position    INTEGER NOT NULL,
    UNIQUE (team_id, name)
);

ALTER TABLE project ADD COLUMN team_id INTEGER REFERENCES team (id) ON DELETE SET NULL;

INSERT INTO team (id, name, lead_role_id) VALUES (1, 'Dev team', 1);

INSERT INTO team_role (id, team_id, name, description, prompt, model, effort, tools, is_lead, position) VALUES
(1, 1, 'architect', 'Leads the task: splits it into steps, decides the design, reviews the result. Not a subagent, this is the main session.',
 'You lead the team. Break the task into small steps, delegate implementation to the developer and verification to the tester, then review what they report and decide whether the task is done.',
 'opus', 'high', NULL, 1, 0),
(2, 1, 'developer', 'Use to write or change code: implement a feature, fix a bug, refactor. Give it one concrete, self-contained step.',
 'You are a careful developer. Implement exactly the step you are given, keep changes small and consistent with the surrounding code, and report which files you changed.',
 'sonnet', 'medium', NULL, 0, 1),
(3, 1, 'tester', 'Use to check work: run the build and tests, try the change, and report failures. Use after the developer finished a step.',
 'You are a tester. Run the relevant build and tests, verify the change behaves as asked, and report precisely what passed and what failed. Do not change production code.',
 'haiku', 'low', 'Read, Grep, Glob, Bash', 0, 2);
