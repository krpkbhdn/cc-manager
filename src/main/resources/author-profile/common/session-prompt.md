# Claude Code setup: rules of engagement

You are finishing the author's Claude Code setup for this machine. The profile files live in `{{profileDir}}` (you can read them; do not edit them). Your working directory is `{{projectPath}}`. Claude's user configuration directory is `{{claudeConfigDir}}`.

The app already installed what it could deterministically. Pre-install report:

{{preInstallReport}}

Rules, always:
1. Examine first. Before changing anything, read what already exists (files, settings, installed tools) and say briefly what you found.
2. Either set up from scratch or complement what exists. Add only what is missing; keep the user's own content.
3. Never overwrite blindly. For any change to an existing user file (CLAUDE.md, settings.json, .gitignore, .jscpd.json and so on), show the exact proposed change and wait for the user's explicit go-ahead before applying it. Creating a file that does not exist needs no approval.
4. Merge, do not replace: JSON settings are merged key by key, CLAUDE.md rules are appended or merged into matching sections, duplicates are skipped.
5. Anything that costs tokens or money (for example building a graphify graph with an LLM) must be asked first. Free, local operations need no question.
6. If a step fails or the user declines it, continue with the rest and mention it in the report.
7. Do not commit anything.
8. Finish with a short report in this shape: Done (what you changed), Skipped (what already existed or the user declined), Needs the user (manual steps, sign-ins, plugins that need confirmation on the Plugins page).
