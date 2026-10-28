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

- Java 25 on **JetBrains Runtime (JBR) 25** (Gradle toolchain `vendor = JETBRAINS`, provisioned via the Foojay resolver, ~670 MB once): Jewel 0.41 classes are Java 25 bytecode, and JBR gives the custom title bar with native snap/maximize (`DecoratedWindow`). Without JBR the app falls back to the plain OS frame. Spring Boot 4.1, Kotlin 2.4 (UI only).
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
- `claude.proposal` — `ProposalService` holds the pending `Proposal` of each held hook (questions, plan approval, tool permission, MCP form; `ProposalParser`), `answer(id, ProposalAnswer)` renders the hook output, `dismiss` hands the dialog back to the terminal; proposals settled elsewhere (PostToolUse of the tool, Stop, new prompt, Esc, exit) are released. Details in `docs/claude-code-protocol.md` section 16.
- `claude.launch` — `ClaudeLauncher` starts `claude` in a pty4j PTY with `CCM_SESSION` env and injected hooks; `BackgroundAgentService` wraps `claude agents --json`.
- `usage` — `RateLimitService` (account-wide 5h/7d `rate_limits`, Pro/Max only) and `SessionUsageService` (per session model, effort, context %, cost from the statusLine payload). `ui/chrome` shows them as the top-bar usage chip with a click-open detail panel (`UsageIndicator`, `UsagePanel`; shortcut Ctrl+Shift+Y, palette "Show usage"); Team plans get an explanation plus the per-session list.
- `project.ModelSelection` — per-project model and reasoning effort (SQLite V3), passed as `--model`/`--effort` by `ClaudeCommand`; `ui/screens/ModelControl` in the session tab strip shows the live model/effort and, for a free session, types `/model`, `/effort` (or Alt+P for Claude's own picker) into the PTY via `SessionCommandWriter`; without a live session it edits the default for the next launch.
- `run` — run configurations per project (SQLite V5) started from the UI: `RunConfigurationService`, `RunService` (one PTY per run); `run.mcp` holds the MCP tools (`list/set/delete_run_configuration`) served by `claude.mcp.McpEndpoint` on the HookServer, and the bundled plugin skill `run-configurations` (`claude.plugin.ClaudePluginWriter`, `src/main/resources/claude-plugin`) makes Claude register a configuration for its git worktree. Details in `docs/claude-code-protocol.md` section 15.
- `shell` — plain interactive shells like IDEA's Terminal: `ShellDetector` lists installed shells (PowerShell 7, Windows PowerShell, cmd, Git Bash, WSL; `/etc/shells` with `$SHELL` first), `ShellLauncher` spawns one in the project directory through the shared `claude.launch.PtySpawner`; not a Claude session (no hooks, never in `SessionRegistry`, ids `shell-<uuid>`). UI: `ProjectTab.Shell` tabs, `ShellsState` (labels, last chosen shell in `UiSettings`, tab closes when the shell exits), terminal button in the tab strip, Alt+F12.
- `ui/assistant` + `ClaudeLauncher.launchAssistant` — the Assistant: a Claude session outside any project in `<dataDir>/assistant` (sentinel `ClaudeLauncher.ASSISTANT_PROJECT_ID = 0`, no team/plugin/MCP, constant `--append-system-prompt`); `AssistantState` (`AppState.assistant`) starts or focuses the single live one and, only for it, polls the terminal screen for Claude's trust-folder dialog and offers an app confirm (Yes sends Down+Enter); `Route.Assistant`, sidebar item, palette entry, Ctrl+Shift+I; project 0 is routed to the Assistant screen by `AppState.routeOf`.
- `workspace` — open folder, open in IntelliJ IDEA, `git diff` parsing.
- `team` — agent teams (SQLite V4, "Dev team" seeded by the migration): `TeamService` CRUD and validation, `TeamAgentsJson` builds the `claude --agents` JSON (one native subagent per non-lead role, with model and effort) and the lead's system prompt addendum; `ClaudeLauncher.launchTeam` starts the lead with the lead role's model/effort, `--agents`, `--append-system-prompt` and the task as the first prompt; every launch from the UI (new, `--continue`, `--resume`) also passes the project's default team (`launch(projectId, mode, team)`, project model/effort win per field over the lead's), re-passed on resume because claude does not persist these flags; `WindowsArgument` quotes arguments because pty4j does not escape quotes on Windows. Details in `docs/claude-code-protocol.md` section 11.
- `author` — the author's bundled Claude Code setup (`src/main/resources/author-profile`, three separately versioned components USER_SETUP, PROJECT_SETUP, TEAMS) behind `AuthorProfileService`. The app installs the deterministic parts (marketplaces, plugins, skills, MCP, teams; a plugin needing confirmation is never auto-accepted), then `ClaudeLauncher.launchSetup` starts a session that finishes CLAUDE.md/settings/graphify/jscpd; the profile is staged in `<dataDir>/author-profile` and passed with `--add-dir`. Decision policy (SQLite V6 `author_decision`): no row or an older installed version is offered, DECLINED is never offered again, a manual install overwrites a decline. UI in `ui/author`: `AuthorProfileState` (`AppState.author`) offers user components at start and a project's setup when it is opened (once per run, never while another author dialog is open); Install installs the checked boxes and declines the unchecked ones, Decline declines all, Later/Esc records nothing; "Author configuration" in the command palette and the project menu always allows Install/Update/Reinstall. Without a project the session cannot start, so a toast asks to add one and nothing is recorded.
- `ui` — design system in `ui/theme` (palette, Inter + JetBrains Mono type scale, motion), Lucide icons in `ui/icons`, native OS window frame (title bar, snap, maximize) with a slim in-app top bar and status bar in `ui/chrome`, global AWT key dispatcher + shortcut table in `ui/shortcuts` (Ctrl+Shift/Alt chords so plain Ctrl+letter reaches the terminal), command palette in `ui/palette`; `Main.kt` → `AppWindow`; `state/Backend` + `AppState` bridge Spring beans to Compose; `ui/terminal` = terminal drawn in pure Compose, never a SwingPanel (Swing paints above Compose and `compose.interop.blending` must stay off, it breaks dragging the custom title bar): `TerminalHost` holds one `TerminalSession` per session (jediterm-core emulation + pty4j connector, scrollback, selection, zoom; survives navigation; closing a claude session sends Ctrl+C and kills only after a grace period, shells are killed at once), `TerminalPainter` draws rows with Skia on an exact cell grid (box and block characters geometrically), `ComposeKeys`/`TerminalInput`/`TerminalPointer` turn Compose events into bytes, selection and mouse reports.
- `ui/teams` — Teams screen (team list, compact roster of agents, every change saved to the team at once), `RoleDialog` for creating/editing an agent (`RoleForm` validated through the backend rules, `ToolPicker`/`ToolSelection` for the tool allowlist), start-task dialog, role strip in the session header, role labels for subagents in the agents rail; `TeamsState` is reached through `AppState.teams`, `AppState.sessionTeams` remembers which team a session was started with.
- `ui/git` — the Git tab (replaces the old Diff tab): `model` holds the per-project `GitState` (status/branches/stashes/tags, one mutation at a time off the UI thread, errors as `ErrorPlan`), `LogModel`, `CommitDraft` and the pure view logic; `changes` (lists, hunk-aware diff pane, commit box), `log` (graph canvas, details), `branch` (popup, list), `dialog`. Git calls are blocking: always run them through `GitState` (Dispatchers.IO).
- `ui/proposal` — pending `ProposalService` proposals as modal dialogs (`ProposalDialogHost` in `AppRoot`, never docked, so the terminal never resizes): `ProposalState` (via `AppState.proposals`) holds pending/hidden proposals and drafts; `AppState.proposalDialog` is the oldest unhidden proposal of the session whose tab is open. One dialog per kind (`QuestionsDialog`, `PlanDialog`, `PermissionDialog`, `FormDialog`) in a shared `ProposalDialogFrame` (`ModalHost` + `DialogBody`). Esc/click outside only hides (the tab-strip "Answer" button or the attention card reopens), "Answer in terminal" dismisses. Key behaviour is pure: `QuestionsDraft.press` (arrows, Enter, Space, Ctrl+Enter, digits), `FormDraft`, `FormSchema`, `ProposalSummary` (also the `AttentionCard` headline). The terminal dialog stays live; whichever answers first wins.
- `ui/sessions` — all-sessions screen (`Route.Sessions`, sidebar item, palette, Ctrl+Shift+S): `SessionBoard` is the pure logic (fixed start-time order, status/project `SessionFilter`, `nextWaiting`, `isStale` against `cc-manager.sessions.working-stale-after`, `groupByProject`; the by-project view is saved in `UiSettings`), `SessionCard` shows project, title, status, last action (`activity`; there is no separate active-command field), time since the last event, context and cost; Answer goes through `AppState.answerProposals`, Ctrl+Shift+N (`answerNextWaiting`) cycles through the waiting sessions.
- `ui/run` — run configurations: `RunState` (reached via `AppState.runs`) keeps per-project configuration lists and the latest run of each, reloads on `RunConfigurationService`/`RunService` change callbacks (Claude adds and deletes configurations at any time) and opens a console in the shared terminal for every new run id, closing the replaced one; `RunControl` (header selector + Run/Restart + Stop), `RunTab` (status row + console, `ProjectTab.Run`), `RunConfigurationDialog`; shortcut Ctrl+Shift+R and palette entries run/stop the selected configuration.
- `prompt` — reusable prompt templates (`PromptTemplateService` CRUD with change callbacks, optional project/default team binding) and `PlaceholderSubstitution` (`{project}`, `{branch}`, `{file}`; unknown or valueless ones stay as typed).
- `ui/prompts` — Prompt Library screen, `PromptDialog`, the shared `PickerDialog` cards (`TemplatePickerDialog`, `ProjectPickerDialog` for Run); `PromptsState` reloads on template, project and team change callbacks. `ui/queue` — per-session prompt queue (`QueueState`, `PromptQueues`, `QueueControl`): a prompt is typed as soon as an idle session may take it, then one per turn end (WORKING to IDLE); the button shows a `CountDot`. `QueueDispatcher` (pure) holds it back while the session is NEEDS_ACTION or blocked, after an Esc interrupt (until the user adds a prompt) and after a send (until the next turn starts) and while an unsent user draft may exist (`TerminalSession.draftMayExist`: set by user input, reset by Enter, Ctrl+C, lone Esc); the draft is checked again right before typing via `SessionCommandWriter`.

Hooks are observe-only except `PermissionRequest` and `Elicitation`: every other hook gets an immediate empty 200, those two are held (up to `cc-manager.hooks.decision-hold`) until the user answers in the app, and an empty 200 leaves the terminal dialog in charge. Claude fires no `Stop` on Esc, so the terminal reports lone-Esc to `SessionRegistry.markInterrupted`. Reference for all events and payloads: `docs/claude-code-protocol.md` (read it before touching hooks).

Dev checks: `./gradlew gitShots` (renders the Git tab offscreen with ImageComposeScene over fake git repositories to PNGs, flags `--out=<dir> --only=<names> --dark|--light --size=WxH`; `./gradlew runPreview` (UI with fake services; flags `--light --many --empty --size=WxH --shots --out=<dir>`; `--real [--noprompt|--attention|--git]` (`--git` adds a throwaway ccm-sandbox/git-demo repo with a local bare origin) boots the real stack on a temp data dir, adds the ccm-sandbox projects and launches real claude sessions, at most a few tiny prompts); `CCM_REAL_CLAUDE=1 ./gradlew test --tests '*RealClaudeLaunchTest'` (real claude through the whole stack, no prompt sent; `*RealRateLimitTest` sends one tiny prompt to check the statusLine post); `gitShots --only=usage-high,usage-none` renders the status bar usage meters; `CCM_SHOT_DIR=<dir> ./gradlew test --tests '*Terminal*'` renders the terminal offscreen (ImageComposeScene, canned and replayed claude output) to PNGs, no OS window or input automation; `CCM_SMOKE=true` runs the hook smoke test (calls the model twice, haiku).

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
- Version: every commit raises the PATCH in `version` of `build.gradle.kts` (X.Y.Z → X.Y.Z+1) and uses the new value in its message; MINOR and MAJOR are raised only when the user says so (then patch resets to 0). Never invent a version.
- Description: English, imperative, no trailing period, max ~80 chars, describes the behavioral/structural change, not code mechanics. 1 change = 1 clause; 2–3 related changes comma-separated. Preferred verbs: add, remove, fix, prevent, ensure, guard, await, sequence, replace, extract, rename, migrate, introduce, drop, restore, handle, wrap, unify, split, merge.
- Forbidden in description: improve, enhance, adjust, tweak, modify, change, optimize, better, cleanup, misc, various, several, some; bare "update" as the main verb; version bump / release X.Y.Z / file names like package.json; meta-mentions of imports, types, signatures.
- Example: `feature(ver. 1.0.0): add home view with project overview`

Code:
- Naming: `*Service`, `*Properties`, `*Configuration`, `*Repository`; Composables in PascalCase, files named after the main Composable (`HomeView.kt`).
- Tests: JUnit 5 + AssertJ + Mockito; one test class per production class; descriptive method names.
- Code and comments in English; UI text is English until localisation is decided.
