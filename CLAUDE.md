## graphify

This project has a graphify knowledge graph at graphify-out/ (built with `/graphify`).

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)

## Project

Claude Code Manager — a cross-platform (Windows/Linux/macOS) **desktop** app that runs and manages many local Claude Code sessions from one place: projects, agents, notifications, approving or rejecting agent proposals. Think "AgentRoom, but better".

Direction (not built yet): one coordinating Claude that routes user requests to agents in concrete projects; later triggers (GLPI, Jira ticket → agent analyses → proposes a solution → user approves/rejects); long-term it may grow into a full IDE.

**Licensing constraint (hard rule):** everything runs locally against the user's own Claude Code installation. No server-side component that hosts or proxies Claude sessions. Do not design features that need one.

## Stack

- Java 25 (Jewel 0.41 classes are Java 25 bytecode; Java 21 fails with `UnsupportedClassVersionError`), Spring Boot 4.1, Kotlin 2.4 (UI only).
- UI: Compose Multiplatform 1.12 + Jewel standalone (`jewel-int-ui-standalone`). Compose is Kotlin-only, hence the Kotlin source set.
- Storage: SQLite (`sqlite-jdbc`, WAL) in `~/.cc-manager/cc-manager.db` (`cc-manager.data-dir`), schema via Flyway SQL migrations in `src/main/resources/db/migration` (`V<n>__name.sql`, never edit an applied one). Access through `JdbcClient`, not JPA.
- Gradle (Kotlin DSL) — the Compose plugin owns desktop packaging (`packageMsi` / `packageDeb` / `packageDmg`), so no Maven.
- Single module for now: `src/main/java` (Spring, domain logic) and `src/main/kotlin` (Compose UI). Package root `com.github.krpkbhdn.ccmanager`; UI in `.ui`.
- Spring runs **in-process without a web server** (`WebApplicationType.NONE`, `headless(false)`); the UI is started from `ui/Main.kt` and calls Spring beans directly.


## Architecture

Packages under `com.github.krpkbhdn.ccmanager` (Java, Spring beans) and `.ui` (Kotlin, Compose):
- `project` — registered project directories (SQLite). `claude.install` — finds the native `claude`, version, login state.
- `claude.hook` — loopback `HookServer` receives Claude Code hooks; `HookSettingsWriter` writes the per-session `--settings` file (all hook events except Setup, Worktree*, MessageDisplay); `HookEventStore` keeps an event log (V2 migration).
- `claude.session` — `StatusReducer` (pure) + `SessionRegistry`: per-session status OFFLINE/IDLE(green)/WORKING(yellow)/NEEDS_ACTION(red), subagents. `notification` — status transitions to notifications.
- `claude.launch` — `ClaudeLauncher` starts `claude` in a pty4j PTY with `CCM_SESSION` env and injected hooks; `BackgroundAgentService` wraps `claude agents --json`.
- `workspace` — open folder, open in IntelliJ IDEA, `git diff` parsing.
- `ui` — design system in `ui/theme` (palette, Inter + JetBrains Mono type scale, motion), Lucide icons in `ui/icons`, undecorated window with own title bar/status bar in `ui/chrome`, command palette in `ui/palette`; `Main.kt` → `AppWindow`; `state/Backend` + `AppState` bridge Spring beans to Compose; `ui/terminal` = JediTerm widget per session (`TerminalHost`), survives navigation.

Hooks are observe-only: the server always answers 200 with an empty body, so the integration never changes Claude's behaviour. Claude fires no `Stop` on Esc, so the terminal reports lone-Esc to `SessionRegistry.markInterrupted`. Reference for all events and payloads: `docs/claude-code-protocol.md` (read it before touching hooks).

Dev checks: `./gradlew runPreview` (UI with fake services; flags `--light --many --empty --size=WxH --shots --out=<dir>`; `--real [--noprompt|--attention]` boots the real stack on a temp data dir, adds the ccm-sandbox projects and launches real claude sessions, at most a few tiny prompts); `CCM_REAL_CLAUDE=1 ./gradlew test --tests '*RealClaudeLaunchTest'` (real claude through the whole stack, no prompt sent); `CCM_SMOKE=true` runs the hook smoke test (calls the model twice, haiku).

## Commands

Run from repo root (use `./gradlew`; Git Bash or PowerShell `.\gradlew.bat`).

```sh
./gradlew run          # launch the desktop app
./gradlew test         # JUnit 5 tests
./gradlew build        # compile + test + jar
./gradlew packageMsi   # installer for the current OS (packageDeb / packageDmg elsewhere; must run on that OS)
```

The `compose.desktop` block points `javaHome` at the Java 25 toolchain; without it `run` uses Gradle's own JDK.

## Duplicate code

- Before writing anything new, search for an existing implementation: `graphify query "<what you need>"` first, then grep.
- After a change, run `npx jscpd --reporters ai <changed paths>` (uses `.jscpd.json`). Remove clones you introduced with the `dry-refactoring` skill.
- Deduplication never changes behaviour.

## Clean code

- Names say intent; no `*Helper`, `*Manager`, `*Info`, `process()`, `handleData()`. Domain terms stay.
- No god methods/classes: a method fits on one screen; ~300 lines in a class means split by responsibility.
- A growing `switch`/`if` chain on a type/kind → strategy (one class per case).
- Constructor injection; immutability (`final`, records, unmodifiable collections); no `null` collections; `Optional` only as a return type.
- Tunables in `@ConfigurationProperties`, never `@Value`; no magic numbers/strings.
- Composables: stateless where possible, state hoisted, one screen per file, UI holds display logic only — work goes in Spring services.
- No secrets or personal data in logs.
- YAGNI: no speculative abstractions or "for later" code; delete dead code.

## Comments

- Names and small methods explain the code; a comment is a hint, not the explanation.
- At most a 1–3 line Javadoc/KDoc before a class or non-obvious method, saying *why*.
- No comments in method bodies except one line for a non-obvious why (workaround, pinned version).
- No change history, task numbers, commented-out code.

## Conventions

Git:
- Commits and PRs carry no AI attribution: no `Co-Authored-By: Claude …`, no "Generated with Claude Code". The author is the git user only.
- Commit only when asked.
- Branches: `main` is stable; all development and testing happens on `develop`.
- Commit message is a single line: `<type>(ver. X.Y.Z): <description>`. Types, first match wins: `fix` (bug fix, error handling, race/order/state problems, guards against wrong behavior; async/sync rewrites that change execution flow) → `feature` (new user-visible capability, component, guard, endpoint, behavior) → `refactor` (restructure with provably identical behavior) → `chore` (dependencies, configs, env, manifests) → `ci` (pipelines, build/deploy scripts). Unsure fix vs refactor → `fix`; unsure feature vs refactor → `feature`.
- Version: the new version if the diff changes it (`version` in `build.gradle.kts`), otherwise reuse the latest from commit history. Never invent one.
- Description: English, imperative, no trailing period, max ~80 chars, describes the behavioral/structural change, not code mechanics. 1 change = 1 clause; 2–3 related changes comma-separated. Preferred verbs: add, remove, fix, prevent, ensure, guard, await, sequence, replace, extract, rename, migrate, introduce, drop, restore, handle, wrap, unify, split, merge.
- Forbidden in description: improve, enhance, adjust, tweak, modify, change, optimize, better, cleanup, misc, various, several, some; bare "update" as the main verb; version bump / release X.Y.Z / file names like package.json; meta-mentions of imports, types, signatures.
- Example: `feature(ver. 1.0.0): add home view with project overview`

Code:
- Naming: `*Service`, `*Properties`, `*Configuration`, `*Repository`; Composables in PascalCase, files named after the main Composable (`HomeView.kt`).
- Tests: JUnit 5 + AssertJ + Mockito; one test class per production class; descriptive method names.
- Code and comments in English; UI text is English until localisation is decided.
