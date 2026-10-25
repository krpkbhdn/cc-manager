## graphify

This project has a graphify knowledge graph at graphify-out/ (built with `/graphify`).

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)

## Duplicate code

- Before writing anything new, search for an existing implementation: `graphify query "<what you need>"` first, then grep.
- After a change, run `npx jscpd --reporters ai <changed paths>` (uses `.jscpd.json`). Remove clones you introduced with the `dry-refactoring` skill.
- Deduplication never changes behaviour.

## Clean code

- Names say intent; no `*Helper`, `*Manager`, `*Info`, `process()`, `handleData()`. Domain terms stay.
- No god methods/classes: a method fits on one screen; ~300 lines in a class means split by responsibility.
- A growing `switch`/`if` chain on a type/kind becomes a strategy (one class per case).
- Prefer constructor injection and immutability; no `null` collections; optional values only as return types.
- Tunables live in configuration, never as magic numbers or strings.
- UI code holds display logic only; work goes in services.
- No secrets or personal data in logs.
- YAGNI: no speculative abstractions or "for later" code; delete dead code.

## Comments

- Names and small methods explain the code; a comment is a hint, not the explanation.
- At most a 1-3 line doc comment before a class or non-obvious method, saying why.
- No comments in method bodies except one line for a non-obvious why (workaround, pinned version).
- No change history, task numbers, commented-out code.

## Conventions

Git:
- Commits and PRs carry no AI attribution: no `Co-Authored-By: Claude`, no "Generated with Claude Code". The author is the git user only.
- Commit only when asked.
- Commit message is a single line: `<type>(ver. X.Y.Z): <description>`. Types, first match wins: `fix` (bug fix, error handling, race/order/state problems, guards against wrong behavior) then `feature` (new user-visible capability) then `refactor` (restructure with provably identical behavior) then `chore` (dependencies, configs, env, manifests) then `ci` (pipelines, build/deploy scripts). Unsure fix vs refactor: `fix`; unsure feature vs refactor: `feature`.
- Version: every commit raises the PATCH of the version in the project's version file (X.Y.Z to X.Y.Z+1) and uses the new value in its message; MINOR and MAJOR are raised only when the user says so (then patch resets to 0). Never invent a version.
- Description: English, imperative, no trailing period, max ~80 chars, describes the behavioral or structural change, not code mechanics. Preferred verbs: add, remove, fix, prevent, ensure, guard, replace, extract, rename, migrate, introduce, drop, restore, handle.
- Forbidden in description: improve, enhance, adjust, tweak, modify, change, optimize, better, cleanup, misc, various, several, some; bare "update" as the main verb; version bump or release wording; file names.

Code:
- Tests: one test class per production class, descriptive method names.
- Code and comments in English.
