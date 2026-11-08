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
