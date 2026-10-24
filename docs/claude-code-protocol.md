# Claude Code integration reference (for cc-manager)

Researched 2026-10-24 against live docs (CLI version family 2.1.2xx-2.1.30x). Base: https://code.claude.com/docs/en/ (docs.claude.com redirects).
Anything not confirmed in docs is marked UNVERIFIED. Docs say: "entry format of transcripts is internal and changes between versions".

Pages used: /hooks (279k chars; fully read except tail past ElicitationResult/prompt-hook section), /hooks-guide, /settings, /cli-reference, /statusline, /sub-agents (first 100k chars), /headless, /sessions, /claude-directory, /env-vars (first 100k chars), /setup.

---------------------------------------------------------------------------
## 1. Hook events  (https://code.claude.com/docs/en/hooks)

Full list (33): SessionStart, Setup, UserPromptSubmit, UserPromptExpansion, PreToolUse, PermissionRequest, PermissionDenied, PostToolUse, PostToolUseFailure, PostToolBatch, Notification, MessageDisplay, SubagentStart, SubagentStop, TaskCreated, TaskCompleted, Stop, StopFailure, TeammateIdle, InstructionsLoaded, ConfigChange, CwdChanged, DirectoryAdded, FileChanged, WorktreeCreate, WorktreeRemove, PreCompact, PostCompact, PreModelSwitch, PostModelSwitch, Elicitation, ElicitationResult, SessionEnd.
(Unknown event name in settings = "Settings Warning", entry skipped, rest of file kept.)

### Common stdin fields (all events)
session_id; prompt_id (UUID of current user prompt = OTel prompt.id; absent before first input); transcript_path (written async, may lag - use last_assistant_message on Stop); cwd (follows cd/worktree); scratchpad_dir (2.1.257+, optional); permission_mode (`default`(=Manual)|`plan`|`acceptEdits`|`auto`|`dontAsk`|`bypassPermissions`); effort {level: low|medium|high|xhigh|max} (tool-context events); hook_event_name; agent_id (present only INSIDE subagent / in-process teammate); agent_type (agent name, e.g. Explore / custom; present with `--agent` OR inside subagent; subagent overrides session --agent). `model` only on SessionStart (optional). No $CLAUDE_MODEL env.
Tool events add: tool_name, tool_input, tool_use_id. File paths absolute, backslashes on Windows. MCP tools: optional `mcp_server` {name, source} (2.1.274+). MCP tool naming `mcp__<server>__<tool>`.

### Event table
| Event | Fires | Matcher on | Event-specific stdin | Output / blocking |
|---|---|---|---|---|
| SessionStart | launch, --continue/--resume, /clear, compaction, fork | source: startup,resume,clear,compact,fork | source; model?; agent_type?; session_title?; (resume/fork only, 2.1.251+) seconds_since_last_response, context_tokens, prompt_cache_likely_expired, estimated_cache_write_usd | only command & mcp_tool handlers; plain stdout -> context; hookSpecificOutput.additionalContext, initialUserMessage (-p only), sessionTitle, watchPaths, reloadSkills. Runs in background at launch (first reply waits). exit2 = stderr to user only. CLAUDE_ENV_FILE available |
| Setup | only `--init-only`, or `--init`/`--maintenance` with -p | init, maintenance | trigger | no blocking, output discarded |
| UserPromptSubmit | user submits prompt (before Claude sees it) | none | prompt, session_title? | decision:"block"+reason (or exit2) blocks; additionalContext, sessionTitle, suppressOriginalPrompt; plain stdout -> context; default timeout 30s |
| UserPromptExpansion | slash command / MCP prompt expands | command name | expansion_type (slash_command|mcp_prompt), command_name, command_args, command_source, prompt | exit2 / decision block |
| PreToolUse | before tool executes | tool_name | tool_name, tool_input, tool_use_id, mcp_server? | hookSpecificOutput{hookEventName, permissionDecision allow|deny|ask|defer, permissionDecisionReason, updatedInput, additionalContext}; exit2 blocks. Precedence deny>defer>ask>allow. `EndConversation` skips |
| PermissionRequest | permission dialog is about to show | tool_name | tool_name, tool_input, permission_suggestions[]?, mcp_server?; (NO tool_use_id) | hookSpecificOutput.decision{behavior allow|deny, updatedInput, updatedPermissions, message, interrupt}; exit2 NOT honored (use decision). Lets an external app answer permission prompts |
| PermissionDenied | auto-mode classifier denial only | tool_name | tool_name, tool_input, tool_use_id, reason | hookSpecificOutput.retry:true |
| PostToolUse | tool succeeded | tool_name | tool_name, tool_input, tool_response(obj), tool_use_id, duration_ms?, mcp_server? (Bash may carry bashEditDiff) | decision block, reason, additionalContext, updatedToolOutput, updatedMCPToolOutput; exit2 stderr -> Claude |
| PostToolUseFailure | tool failed | tool_name | tool_name, tool_input, tool_use_id, error(str; Bash first line "Exit code N"), is_interrupt?, duration_ms? | like PostToolUse. NOT fired for pre-exec rejections or permission denials; cancelling a running tool does not fire it |
| PostToolBatch | after parallel batch, before next model call | none | tool_calls[]{tool_name,tool_input,tool_use_id,tool_response(serialized)} | additionalContext; exit2 stops loop |
| Notification | Claude Code raises a notification | notification_type | message, title?, notification_type | cannot block; only terminalSequence honored |
| MessageDisplay | text streaming to screen | none | turn_id, message_id, index, final, delta | displayContent (display only); timeout 10s |
| SubagentStart | subagent spawned | agent type | agent_id, agent_type | additionalContext; cannot block |
| SubagentStop | subagent finishes | agent type | stop_hook_active, agent_id, agent_type ("" for internal agents e.g. /btw), agent_transcript_path (…/<session>/subagents/…), last_assistant_message, background_tasks[], session_crons[] ; transcript_path = PARENT transcript | decision block / exit2 keeps it running |
| TaskCreated / TaskCompleted | task tool create/complete (also teammate turn end w/ in-progress tasks) | none | task_id, task_subject, task_description?, teammate_name?, team_name?(deprecated), agent_id?(2.1.290+) | exit2 / decision block (Created: rolls back) |
| Stop | main agent finished responding (every turn) | none | stop_hook_active, last_assistant_message, background_tasks[], session_crons[] | decision block+reason / exit2 / additionalContext keep Claude going; 8-consecutive-continuation cap (CLAUDE_CODE_STOP_HOOK_BLOCK_CAP). **Does NOT fire on user interrupt; API errors fire StopFailure instead** |
| StopFailure | turn ended by API error (instead of Stop) | error type | error (rate_limit, overloaded, authentication_failed, oauth_org_not_allowed, account_on_hold, billing_error, invalid_request, model_not_found, server_error, max_output_tokens, cloud_credential_error, unknown), error_details?, last_assistant_message (= API error string) | no decision; output ignored |
| TeammateIdle | agent-team teammate about to idle | none | teammate_name, team_name(deprecated), agent_id? | exit2 keeps working; {continue:false,stopReason} |
| InstructionsLoaded | CLAUDE.md / rules loaded | load_reason: session_start,nested_traversal,path_glob_match,include,compact | file_path, memory_type (User|Project|Local|Managed), load_reason, globs?, trigger_file_path?, parent_file_path? | none |
| ConfigChange | settings file/skill file changed mid-session | user_settings,project_settings,local_settings,policy_settings,skills | source, file_path? | decision block (not policy_settings) |
| CwdChanged | Bash `cd` in main conversation | none | old_cwd, new_cwd | watchPaths[]; CLAUDE_ENV_FILE available |
| DirectoryAdded | /add-dir or SDK register_repo_root (NOT --add-dir) | slash_command, register_repo_root | directory, source | none; runs in background |
| FileChanged | watched file changes (fs watcher) | literal filenames split on `|` (also builds watch list) | file_path, event (change|add|unlink) | watchPaths[]; CLAUDE_ENV_FILE available |
| WorktreeCreate | worktree creation (--worktree, EnterWorktree, subagent isolation, bg session) | none | name | REPLACES git behaviour; must return path (command: last stdout line; http: hookSpecificOutput.worktreePath). non-zero fails |
| WorktreeRemove | cleanup of hook-created worktree | none | worktree_path | exit code only |
| PreCompact | before compaction | manual, auto | trigger, custom_instructions (null if none) | exit2 / decision block |
| PostCompact | after compaction | manual, auto | trigger, compact_summary | none |
| PreModelSwitch (2.1.251+) | before requested model switch (/model, picker, /config, fast mode, SDK set_model) | canonical model name (regex ok) | from_model, to_model, requested_model, source (command|picker|sdk), context_tokens, prompt_cache_warm, cache_ttl, estimated_cache_write_usd, pricing | permissionDecision allow|deny|ask / exit2 / decision block; timeout => blocks; default 30s |
| PostModelSwitch | after model changes (also auto fallback, resume restore) | same | same + source auto|resume | additionalContext / plain stdout |
| Elicitation | MCP server requests user input | MCP server name | mcp_server_name, message, mode (form|url)?, url?, elicitation_id?, requested_schema? | hookSpecificOutput{hookEventName:"Elicitation", action accept|decline|cancel, content}; exit2 = decline |
| ElicitationResult | user's answer about to go to MCP server | MCP server name | mcp_server_name, action, mode?, elicitation_id?, content? | same action/content to override; exit2 ignored per exit-code table |
| SessionEnd | session ends | reason: clear, resume, logout, prompt_input_exit, other | reason | no decision. **Default budget 1.5 s** (raise via per-hook timeout, up to 60s, or CLAUDE_CODE_SESSIONEND_HOOKS_TIMEOUT_MS). Also runs on SIGTERM. `bypass_permissions_disabled` removed 2.1.234 |

### Notification.notification_type (exact semantics)
- permission_prompt: tool-use approval or sandbox network request prompt **has waited ~6 s** (timer restarts on each keystroke in terminal; in SDK/Desktop hosts fixed ~6s, not deferred by typing; disable with CLAUDE_CODE_DISABLE_PERMISSION_PROMPT_NOTIFY_HOOKS=1)
- idle_prompt: Claude finished ~60 s ago, user hasn't typed, no background agent running
- auth_success; elicitation_dialog (form, user idle ~6s); elicitation_url_dialog (~6s); elicitation_complete; elicitation_response
- agent_needs_input / agent_completed: only relevant to agent view (`claude agents`) background sessions; agent_needs_input also for team-teammate setup question / auto-mode classifier cost notice (idle ~6s)
- quota_auto_resume_fired / _stale / _disabled (2.1.234+); plugin_notification (from mods)
- Shares timing with desktop notifications: **in a focused terminal you may never get permission_prompt promptly (~6s delay, and "mostly when you appear away")**. Use PermissionRequest for instant detection.
- Example sample: {"message":"Claude needs your permission","title":"Permission needed","notification_type":"permission_prompt"} (illustrative, not documented constants). Message strings are NOT stable.

### Exit codes (all command hooks)
0 = success; stdout parsed as JSON only if it is exactly ONE JSON object (else plain text; plain text becomes context only for UserPromptSubmit, UserPromptExpansion, SessionStart, PostModelSwitch). 2 = blocking error, stderr is reason, overrides JSON (exceptions: PermissionRequest, Elicitation/ElicitationResult). Any other = non-blocking error (first stderr line shown) unless onFailure:"block".
Universal JSON output: continue (false stops Claude entirely), stopReason, suppressOutput, systemMessage, terminalSequence (OSC 0/1/2/9/99/777 or BEL only; interactive only). additionalContext/systemMessage/initialUserMessage/stdout capped 10,000 chars. hookSpecificOutput requires hookEventName. Misplaced top-level fields silently ignored (see debug log "Hook JSON output had unrecognized keys").

---------------------------------------------------------------------------
## 2. Handler types (https://code.claude.com/docs/en/hooks, /hooks-guide)

Structure: `hooks[Event] = [ { "matcher": "...", "hooks": [ handler, ... ] } ]`.
Common handler fields: type (command|http|mcp_tool|prompt|agent), if (single permission-rule string like "Bash(git *)"; ONLY evaluated on Pre/PostToolUse, PostToolUseFailure, PermissionRequest, PermissionDenied - elsewhere hook never runs), timeout (seconds; defaults 600 command/http/mcp_tool, 30 prompt, 60 agent; 30 on UserPromptSubmit/Pre+PostModelSwitch; 10 MessageDisplay), statusMessage, once (skill frontmatter only).
Matcher: `*`/""/omitted = all; only [A-Za-z0-9_- ,|] = exact or list split on | or ,; otherwise JS regex (unanchored). FileChanged/StopFailure: only `|`.
Matching hooks run in parallel; identical handlers across settings files dedupe.

- command: {type, command, args[] (exec form, no shell; on Windows must be real exe not .cmd/.bat), async:bool, asyncRewake:bool (bg; exit2 wakes Claude), shell:"bash"|"powershell" (default bash, or powershell on Windows w/o Git Bash), onFailure:"continue"|"block" (2.1.295+)}. Shell form uses sh -c / Git Bash / PowerShell. async:true => timeout not enforced, cannot block.
- **http**: {type:"http", url (req), headers:{k:"Bearer $VAR"|"${VAR}"}, allowedEnvVars:[names] (only listed vars interpolated; others -> empty string), timeout, onFailure:"continue"(default)|"block"}.
  - Request: POST, Content-Type: application/json, body = same JSON as command stdin.
  - Response: 2xx + empty body = success; 2xx + JSON object = parsed with same output schema as command hooks; 2xx non-JSON/plain text = non-blocking error, NOT added to context; non-2xx = non-blocking error; **connection failure = non-blocking (action continues)**; timeout = canceled. Blocking needs 2xx JSON with decision fields, or onFailure:"block".
  - No `async` field documented for http (UNVERIFIED whether http supports background; docs list `async`/`asyncRewake` only under command). Keep server fast/respond immediately; default timeout 600s would hang a turn if server stalls - **always set a short `timeout` (e.g. 2-5)**.
  - Admin knobs: allowedHttpHookUrls, httpHookAllowedEnvVars (managed settings). Example from docs: url http://localhost:8080/hooks/pre-tool-use.
  - Not allowed handler type for SessionStart: only command and mcp_tool (so http cannot observe SessionStart!). UNVERIFIED for other events' restrictions beyond this; docs mention Setup also skips mcp_tool.
- mcp_tool: {server, tool, input{"${tool_input.file_path}" substitution}}; skipped on SessionStart at launch and Setup.
- prompt: {prompt ($ARGUMENTS placeholder), model, timeout, continueOnBlock}; LLM must return {ok, reason, impossible?}.
- agent (experimental): same prompt/model; subagent up to 50 turns, returns {ok,reason}.
- Concurrency: >12 prompt/agent hooks queue.
- Background-after-decision: for command hooks, print decision first then background with redirects.

---------------------------------------------------------------------------
## 3. Injecting hooks per launch without touching user files (https://code.claude.com/docs/en/settings, /cli-reference)

- `claude --settings <path-or-inline-json>`: "Values override the same keys in settings.json files for this session. Keys you omit keep their file-based values." Must be a regular file <= 2 MiB. Precedence (high->low): managed > **--settings (command line)** > .claude/settings.local.json > .claude/settings.json > ~/.claude/settings.json. Can set any key user settings can; cannot set Managed/Global-config keys (~/.claude.json keys).
- **Lists merge** (e.g. permissions.allow). For `hooks`: identical handlers across files run once; hooks from --settings are ADDED alongside user/project hooks (docs state merge rule for list keys; the hooks-specific merge is implied by "Identical handlers in multiple settings files run once" - treat as additive; UNVERIFIED by experiment). Plan: verify with a smoke test that user's own hooks still fire AND yours fire.
- `--setting-sources user,project,local`: restricts which file sources load (does NOT affect --settings). `--safe-mode` disables all hooks except managed. `--bare` skips hook auto-discovery (then only --settings hooks apply). `--restricted` loads only managed + --settings.
- disableAllHooks setting kills hooks (not managed ones unless set in managed). allowManagedHooksOnly blocks user/project/local/plugin hooks - **check `claude doctor`/settings for it on managed machines; --settings hooks may then be ignored** (UNVERIFIED whether --settings counts as blocked; docs say it blocks "user/project/local/plugin").
- Edits to settings files are hot-reloaded in running sessions (hooks included); ConfigChange fires.
- Hooks do NOT re-run on --resume transcript replay.
- Env alternatives: none for injecting hooks. `env` block in settings can set env vars. Per-session env must go on the process env when spawning.
- Settings JSON shape:
```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "Bash|Edit", "hooks": [
        { "type": "http", "url": "http://127.0.0.1:PORT/hook", "timeout": 5,
          "headers": { "X-CCM-Session": "$CCM_SESSION" }, "allowedEnvVars": ["CCM_SESSION"] }
      ] }
    ],
    "Stop": [ { "hooks": [ { "type": "http", "url": "http://127.0.0.1:PORT/hook", "timeout": 5 } ] } ]
  }
}
```
- Windows quoting: pass a **temp file path** (`--settings C:\Users\...\ccm-xxxx.json`), not inline JSON (cmd/PowerShell/ConPTY quoting of `"` is fragile). Delete after session ends; file can be read when launched (re-read on change since watched) so keep it for the session lifetime.
- Identify session in hook: payload carries session_id; to map hook -> your tab before session_id is known, pass your own id via env var (CCM_SESSION) + header with allowedEnvVars, or pre-assign `--session-id <uuid>`.

---------------------------------------------------------------------------
## 4. CLI (https://code.claude.com/docs/en/cli-reference, /headless, /setup)

Commands: `claude`, `claude "q"`, `-p`, `-c/--continue`, `-r/--resume <id|name|path.jsonl>`, `update`, `install [version|stable|latest]`, `auth login|logout|status` (status = JSON, exit 0 logged in / 1 not; `--text`; fields authMethod none|claude.ai|oauth_token|api_key|api_key_helper|third_party, configDirectory (2.1.268+)), `doctor` (read-only diagnostics, no session), `mcp` (+ `mcp login|logout <name>`), `plugin(s)`, `agents` (agent view; `--json` prints active sessions array, `--json --all` incl. completed bg sessions; `--cwd <path>`; requires TTY to open UI), `attach <id|name>`, `logs <id|name>`, `stop|kill <id>`, `respawn <id>`, `rm <id>`, `daemon status|logs|run|stop`, `purge [path]`, `remote-control`, `setup-token`, `ultrareview`, `gateway`, `import`, `auto-mode defaults|config|reset`. `--version/-v` prints e.g. `2.1.211 (Claude Code)`.

Flags relevant to a GUI manager:
- --resume/-r (ID, name, or absolute .jsonl path; ID search: current project+worktrees, then all projects; picker if no arg); --continue/-c (most recent in cwd; skips -p/SDK//loop sessions); --fork-session; --session-id <UUID> (valid UUID required); --name/-n; --from-pr.
- --permission-mode default|acceptEdits|plan|auto|dontAsk|bypassPermissions|manual(alias default, 2.1.200+); --dangerously-skip-permissions (= bypassPermissions); --allow-dangerously-skip-permissions; --allowedTools / --disallowedTools / --tools.
- --model (alias sonnet|opus|haiku|fable or full ID), --effort low|medium|high|xhigh|max|ultracode, --fallback-model, --advisor.
- --add-dir (not restored on resume - pass again), --worktree/-w [name|#PR] (+ --tmux), --agent <name>, --agents '<json>' (or file path with -p, 2.1.281+), --append-system-prompt[-file], --system-prompt[-file], --append-subagent-system-prompt (-p only).
- --mcp-config <files|strings>, --strict-mcp-config, --plugin-dir, --plugin-url, --settings, --setting-sources, --bare, --safe-mode, --restricted, --ide, --chrome/--no-chrome, --debug[=filter], --debug-file <path>, --verbose, --teammate-mode, --remote-control/--rc [name], --bg/--background (background agent; not with -p; `--bg --exec cmd`), --desktop.
- Print mode (-p): --output-format text|json|stream-json; --input-format text|stream-json; --include-partial-messages (needs stream-json; also --verbose recommended); --include-hook-events (hook_started/progress/response in stream; SessionStart/Setup always included); --replay-user-messages; --forward-subagent-text (subagent text with parent_tool_use_id); --max-turns; --max-budget-usd; --json-schema; --permission-prompt-tool mcp__srv__tool; --permission-prompts host|none (2.1.259+); --no-session-persistence; --prompt-suggestions; --init/--maintenance/--init-only.
- stream-json: first event `system/init` (model, tools, mcp_servers, plugins, capabilities[]); `system/api_retry` {attempt,max_retries,retry_delay_ms,error_status,error,...}; final `result` message with cost/session; `permission_denied` system messages; stream_event deltas with --include-partial-messages; `parent_tool_use_id` null for main conversation. Exact per-type field lists: /en/agent-sdk/typescript (NOT read - UNVERIFIED here). Input stream-json message schema UNVERIFIED (see agent-sdk docs).
- NOTE: for an interactive TUI embedded in a PTY, none of the stream-json flags apply; hooks are the observation channel. stream-json -p mode is an alternative (structured, but then you render the UI yourself and permission prompts go via --permission-prompt-tool / SDK control requests).
- Resume does not restore: --mcp-config, --settings, --plugin-dir, --fallback-model, --add-dir. Pass again. Permission mode IS restored on terminal --continue/--resume (except bypassPermissions; plan restores).
- Resuming a session that is still running as a background session attaches to it.
- Since 2.1.302: `claude -p` starts in Manual mode when nothing sets a mode.

### Detecting installation
- Native installer: macOS/Linux/WSL `~/.local/bin/claude` (symlink -> `~/.local/share/claude/versions/<ver>`); Windows `%USERPROFILE%\.local\bin\claude.exe` (+ `%USERPROFILE%\.local\share\claude`). Auto-updates in background.
- npm: `@anthropic-ai/claude-code` (Node 22+, installs the same native binary; shim name on Windows likely claude.cmd - UNVERIFIED, docs only say binary is linked by postinstall). Homebrew cask `claude-code` / `claude-code@latest`; WinGet `Anthropic.ClaudeCode`; apt/dnf/apk packages `claude-code`.
- Strategy: resolve `claude` via PATH (`where claude` on Windows; try claude.exe, claude.cmd), fall back to `%USERPROFILE%\.local\bin\claude.exe`, then run `claude --version` (parse `^\d+\.\d+\.\d+`), `claude auth status` (JSON), `claude doctor` for health. Feature-gate flags on version (many flags 2.1.2xx+).
- Env controlling updates: DISABLE_AUTOUPDATER=1 (background only), DISABLE_UPDATES (blocks all), CLAUDE_CODE_PACKAGE_MANAGER_AUTO_UPDATE.

---------------------------------------------------------------------------
## 5. Environment variables (https://code.claude.com/docs/en/env-vars, /hooks)

Passed to hooks/subprocesses: CLAUDE_PROJECT_DIR (session-start dir; unchanged by worktree), CLAUDE_PLUGIN_ROOT/DATA, CLAUDE_PLUGIN_OPTION_<KEY>, CLAUDE_EFFORT, CLAUDECODE=1, CLAUDE_CODE_CHILD_SESSION=1 (nested session detection), CLAUDE_CODE_REMOTE ("true" web only), CLAUDE_CODE_BRIDGE_SESSION_ID, CLAUDE_CODE_MESSAGING_SOCKET/TOKEN (per-session; cannot be set via settings env), **CLAUDE_ENV_FILE only for SessionStart, Setup, CwdChanged, FileChanged** (not PowerShell hooks; writes of `export NAME=value` persist into later Bash commands). Hooks inherit the parent env except OTEL_* exporter vars (more with CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1). Hooks cwd = current cwd (fallbacks if deleted). Path placeholders in config: ${CLAUDE_PROJECT_DIR}, ${CLAUDE_PLUGIN_ROOT}, ${CLAUDE_PLUGIN_DATA}.
Launcher-settable (set in spawn env; "env" block in settings also works for most but not CLAUDE_CONFIG_DIR / telemetry exporters from project settings):
- CLAUDE_CONFIG_DIR (moves ~/.claude contents), CLAUDE_CODE_PROJECT_DIR_NAME (needs CLAUDE_CONFIG_DIR; 2.1.234+; 1-64 chars), CLAUDE_CODE_TMPDIR.
- Notifications/title: CLAUDE_CODE_DISABLE_TERMINAL_TITLE=1 (also disables session-title request + terminal status reports), CLAUDE_CODE_DISABLE_NOTIFICATION_PRESENCE_CHECK=1, CLAUDE_CLIENT_PRESENCE_FILE, CLAUDE_CODE_DISABLE_PERMISSION_PROMPT_NOTIFY_HOOKS=1.
- Telemetry: CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC (any non-empty incl "0"; also disables autoupdate), DISABLE_TELEMETRY, DISABLE_ERROR_REPORTING, CLAUDE_CODE_ENABLE_TELEMETRY=1 (OTel; see /monitoring-usage - NOT read, UNVERIFIED details), CLAUDE_CODE_DISABLE_FEEDBACK_SURVEY.
- IDE: CLAUDE_CODE_AUTO_CONNECT_IDE (false/true; beats autoConnectIde setting), CLAUDE_CODE_IDE_SKIP_AUTO_INSTALL, CLAUDE_CODE_IDE_HOST_OVERRIDE, CLAUDE_CODE_IDE_SKIP_VALID_CHECK.
- Shell/Windows: CLAUDE_CODE_GIT_BASH_PATH, CLAUDE_CODE_USE_POWERSHELL_TOOL (0/1).
- Rendering: CLAUDE_CODE_NO_FLICKER=1 (fullscreen), CLAUDE_CODE_DISABLE_ALTERNATE_SCREEN=1 (classic renderer; best for embedding?), CLAUDE_CODE_DISABLE_MOUSE[_CLICKS], CLAUDE_CODE_DISABLE_VIRTUAL_SCROLL.
- Behavior: CLAUDE_CODE_DISABLE_BACKGROUND_TASKS, CLAUDE_CODE_DISABLE_AGENT_VIEW, CLAUDE_CODE_DISABLE_CRON, CLAUDE_CODE_DISABLE_AUTO_MEMORY, CLAUDE_CODE_SIMPLE (= --bare), CLAUDE_CODE_SAFE_MODE, CLAUDE_CODE_STOP_HOOK_BLOCK_CAP, CLAUDE_CODE_SESSIONEND_HOOKS_TIMEOUT_MS, CLAUDE_CODE_RESUME_INTERRUPTED_TURN, CLAUDE_CODE_SKIP_PROMPT_HISTORY (no transcript writes), CLAUDE_CODE_FORWARD_SUBAGENT_TEXT, CLAUDE_CODE_SUBAGENT_MODEL, CLAUDE_CODE_EFFORT_LEVEL (beats --effort), ANTHROPIC_MODEL (beaten by --model), CLAUDE_CODE_PROVIDER_MANAGED_BY_HOST (host owns model config), MCP_TIMEOUT, CLAUDE_CODE_DEBUG_LOGS_DIR (file path, despite name), CLAUDE_CODE_DEBUG_LOG_LEVEL.
- Precedence: settings `env` block replaces shell value in most sessions; settings can't unset (set ""); shell vars read at startup only. Some DISABLE_* vars must be in launching env (settings env ignores them).
- Not found on the pages read: CLAUDE_CODE_SHELL, CLAUDE_CODE_ENTRYPOINT, CLAUDE_CODE_SESSION_ID (second half of env-vars page, chars 100000+, not read) - UNVERIFIED.

---------------------------------------------------------------------------
## 6. On-disk data (https://code.claude.com/docs/en/claude-directory, /sessions, /sub-agents)

Root = `~/.claude` (= `%USERPROFILE%\.claude` on Windows; `CLAUDE_CONFIG_DIR` overrides everything under it).
- Transcript: `~/.claude/projects/<project>/<session-id>.jsonl`; `<project>` = absolute cwd with every non-alphanumeric char replaced by `-` (e.g. `C--Users-Bohdan-Work-Code-Personal-cc-manager`; >200 chars truncated to 200 + hash). One JSON object per line (message / tool use / metadata). Format internal, version-unstable - parse defensively.
- Subagent transcripts: `projects/<project>/<session-id>/subagents/…` (path given as agent_transcript_path in SubagentStop). Large tool outputs: `<session>/tool-results/`. `.orphaned-<ts>-<suffix>.jsonl` / `.jsonl.superseded-<ts>` = set-aside copies, not in picker (ignore).
- `~/.claude/sessions/`: one small file per RUNNING session, removed on exit, crash leftovers cleared next launch (used by CC to detect concurrent sessions/crashes). File format UNVERIFIED - candidate for liveness/crash detection; inspect on disk.
- `~/.claude/history.jsonl` (typed prompts + timestamp + project path), `stats-cache.json` (usage totals), `file-history/<session>/`, `plans/`, `tasks/`, `debug/<session-id>.txt`, `session-env/`, `shell-snapshots/`, `jobs/` and `daemon/` (+ `daemon.log`) for background sessions, `plugins/installed_plugins.json`, `.credentials.json`, `agent-memory/`, `backups/`.
- Retention: cleanupPeriodDays default 30 (min 1) deletes transcripts older than that -> app should keep its own session index if it needs longer history. Desktop/Cowork transcripts exempt.
- `~/.claude.json` (global, written by CC; don't edit): account details, user-scope MCP server configs, per-project state (trust decisions, project entry keyed by path, history), global config keys. Backups in `~/.claude/backups/.claude.json.backup.<ts>` (5 newest).
- Scratchpad: Windows `%TEMP%\claude\<project>\<session-id>\scratchpad\` (session has one only with claude.ai login + Anthropic API).
- Session listing options: (a) scan projects/*/*.jsonl (mtime, first prompt), (b) `claude agents --json [--all]` (active/bg sessions, name default `<dirname>-<2 chars>`), (c) own index from hooks.
- Session naming: `--name`; unnamed sessions get default display name (NOT resumable) + generated title (resumable; -p runs get none). SessionStart/UserPromptSubmit payload has `session_title` (custom only). hookSpecificOutput.sessionTitle sets it.
- Sessions created with -p/SDK and `/loop`-first sessions are hidden from picker/--continue but resumable by ID.
- Agent definitions: priority managed > `--agents` > `.claude/agents/` (project, scanned recursively) > `~/.claude/agents/` > plugin `agents/`. Markdown with YAML frontmatter + body = system prompt. Frontmatter (camelCase; only name+description required; unknown ignored): name (<=256, no `:`, not leading `-`; hooks receive it as agent_type), description, tools (csv or list), disallowedTools, model (sonnet|opus|haiku|fable|full id|inherit), permissionMode (default|manual|acceptEdits|auto|dontAsk|bypassPermissions|plan), maxTurns, autoCompactWindow (100000-1000000), skills, mcpServers, hooks, memory (user|project|local), background, omitClaudeMd, effort (low..max), isolation (worktree), color (red|blue|green|yellow|purple|orange|pink|cyan), initialPrompt, experimental{cacheTtl}. Built-in agent types seen: general-purpose, Explore, Plan. Plugin agents are namespaced `plugin:name`.
- Project/local settings: `.claude/settings.json`, `.claude/settings.local.json` (in git repo lives at repo root since 2.1.211, except Windows/home-root/ownership cases -> stays with settings.json), `.mcp.json`, `CLAUDE.md`, `CLAUDE.local.md`. Managed: `managed-settings.json` (OS-specific system dir) / MDM / server-managed.

---------------------------------------------------------------------------
## 7. Statusline contract (https://code.claude.com/docs/en/statusline)

Config: `"statusLine": {"type":"command","command":"<script or inline>","padding":0,"refreshInterval":N(seconds,min 1)}`. Can be supplied via --settings. Runs on events (new assistant message, mode change, etc.), debounced; add refreshInterval for idle periods. Script reads JSON on stdin, prints text (ANSI ok, multi-line ok). **A custom statusline hides most footer hints** (esc to interrupt etc.) - side effect for users; and it REPLACES the user's own statusLine if you set it via --settings (key override, not merge). Prefer hooks unless you need cost/context.
Stdin fields: model.id, model.display_name; cwd == workspace.current_dir; workspace.project_dir, workspace.added_dirs[], workspace.git_worktree?; cost.total_cost_usd (client-side estimate, resets on /clear), cost.total_duration_ms, cost.total_api_duration_ms; context_window.context_window_size, used_percentage, remaining_percentage, current_usage{token counts of last API call}; exceeds_200k_tokens; fast_mode; effort.level?; thinking.enabled; prompt_cache{warm, caching_observed, ttl, expires_at, requests, misses, expected_rebuilds, hit_ratio, cache_write_tokens, miss_recache_tokens, last_miss_at, last_miss_cause, miss_causes, recache_tokens_if_cold} (2.1.251+); session_id; session_name?; prompt_id?; transcript_path; version; output_style.name; vim.mode?; agent.name?; pr{review_state, kind?}; worktree{name,path,branch?,original_cwd,original_branch?}; rate_limits{five_hour{used_percentage,resets_at}, seven_day{...}, spend_limit{used_percentage,resets_at,used_usd?,limit_usd?,period?}} (only claude.ai Pro/Max or gateway; only after first API response; each window may be absent). resets_at = epoch seconds.
Plan: statusline script that POSTs its stdin to the manager's local HTTP endpoint (curl.exe / node) and prints nothing or a short string. Needs a process spawn per refresh (cost/latency) - UNVERIFIED debounce interval.

---------------------------------------------------------------------------
## 8. Recommended per-session status mapping

Subscribe (http hooks, short timeout): SessionStart*, UserPromptSubmit, PreToolUse, PermissionRequest, PostToolUse, PostToolUseFailure, Notification, SubagentStart, SubagentStop, Stop, StopFailure, PreCompact, PostCompact, SessionEnd, Elicitation, ElicitationResult, (optional ConfigChange/CwdChanged/TaskCompleted).
*SessionStart rejects http handlers per docs (only command/mcp_tool) -> use a `command` handler (e.g. curl.exe/node one-liner posting stdin) for SessionStart, or skip and infer start from the first event / from pre-assigned --session-id. Note SessionStart/Setup hook events are also emitted in stream-json.

State machine (main agent only: IGNORE events where agent_id is present for status, track them as subagent children):
- IDLE: initial after SessionStart (any source) ; after Stop ; after StopFailure (mark IDLE+error badge with `error` enum; rate_limit/overloaded are retryable) ; after SessionEnd => ENDED.
- WORKING: UserPromptSubmit ; PreToolUse / PostToolUse / PostToolUseFailure (main-agent) after a pending state - these resume WORKING; SubagentStart (inc. count); PreCompact (until PostCompact).
- NEEDS_USER_ACTION: PermissionRequest (instant, preferred; no tool_use_id - correlate by tool_name+time) ; Notification.notification_type in {permission_prompt, elicitation_dialog, elicitation_url_dialog, agent_needs_input} ; Elicitation event (until ElicitationResult).
  Clear NEEDS_USER_ACTION -> WORKING on the next PostToolUse / PostToolUseFailure / PreToolUse of a different tool_use_id / ElicitationResult / UserPromptSubmit. If the user denied the permission: no PostToolUse(Failure) fires for permission denials ("does not fire ... for permission denials") -> then Claude usually continues with new tool call or Stop; a following Stop/PreToolUse/UserPromptSubmit resolves it. Hook-based apps cannot see the denial itself (UNVERIFIED if a PermissionDenied hook fires for manual denials - docs say PermissionDenied is auto-mode classifier only).
- idle_prompt (~60s after finish): state already IDLE; use only as "waiting for you" nudge / badge.
- Plan approval (ExitPlanMode) and AskUserQuestion: NOT documented as Notification types. ExitPlanMode / AskUserQuestion are tools -> they surface as PreToolUse (tool_name ExitPlanMode / AskUserQuestion) and likely PermissionRequest for ExitPlanMode (UNVERIFIED). Practical rule: on PreToolUse with tool_name in {AskUserQuestion, ExitPlanMode} set NEEDS_USER_ACTION until the matching PostToolUse (tool_use_id) arrives. Also `permission_mode` field in payloads reveals plan mode (`plan`).
- Edge cases:
  - **User Esc-interrupt does NOT fire Stop** (docs: Stop doesn't fire on user interrupts); PostToolUseFailure with is_interrupt:true "for an abort" but "cancelling a running tool doesn't fire this hook". So an interrupted turn can leave you stuck in WORKING. Mitigations: (1) detect Esc yourself in the PTY input layer (you own the keystrokes) and set IDLE; (2) idle_prompt after 60s as safety net; (3) tail transcript mtime / last record for an interrupt marker (format UNVERIFIED); (4) next UserPromptSubmit resets anyway.
  - API error ends turn -> StopFailure (not Stop). Always handle both.
  - Stop fires at every turn end, even if background tasks pending: check Stop.background_tasks[] / session_crons[] non-empty => "IDLE but background work in flight / will self-wake" (show as WORKING-background or distinct state). A Stop-hook block (decision:block) re-continues; don't block from the manager.
  - Subagents: events with agent_id are the subagent's; main Stop only after subagents done (SubagentStop precedes). SubagentStop.agent_type "" = internal helper (ignore).
  - Compaction: PreCompact/PostCompact (trigger auto|manual) -> WORKING(compacting). Auto compaction can occur mid-turn.
  - Process crash / kill / terminal close: no hooks guaranteed. SessionEnd runs on exit, /clear, resume switch, and SIGTERM, but with 1.5s budget; a hard crash fires nothing. Use PTY child exit as the authority, plus `~/.claude/sessions/` leftover files (UNVERIFIED format).
  - /clear: SessionEnd(reason clear) then SessionStart(source clear) with NEW session_id -> same tab, rekey. `/resume` in-session: SessionEnd(resume) then SessionStart(resume). Compaction fires SessionStart(source compact). Fork: SessionStart(fork).
  - HTTP hook failures are non-blocking: if manager is down, Claude continues; on manager restart you've lost state -> resync from transcript tail or wait for next event.
  - Hook payload `permission_mode` gives live mode (default/plan/acceptEdits/auto/dontAsk/bypassPermissions) per event.
  - Notification delay: permission_prompt only ~6s after the prompt shows (and typing resets it) - so don't rely on it alone for fast "needs action" UI; PermissionRequest is the immediate signal.
  - In `claude -p`/SDK hosts, permission prompts go through canUseTool/--permission-prompt-tool, not the TUI.

---------------------------------------------------------------------------
## 9. Windows specifics (https://code.claude.com/docs/en/setup, /hooks)

- Native Windows supported (Win10 1809+). No sandboxing on native Windows (WSL2 only).
- Shell: Git for Windows (Git Bash) is OPTIONAL; with it, Bash tool uses Git Bash (and Monitor tool needs it); without it Claude uses the PowerShell tool. CLAUDE_CODE_GIT_BASH_PATH (e.g. C:\\Program Files\\Git\\bin\\bash.exe) if not found. CLAUDE_CODE_USE_POWERSHELL_TOOL=0/1.
- Hooks on Windows: shell-form command hooks run via Git Bash (or PowerShell if Git Bash missing; handler `shell:"powershell"` forces PS). **Without Git Bash, shell-form PreToolUse/PermissionRequest command hooks that fail can block the tool call** - set onFailure:"continue", shell:"powershell", or use exec form. Exec form (`command`+`args`) must be a real .exe (no .cmd/.bat), e.g. `"command":"powershell.exe","args":["-NoProfile","-ExecutionPolicy","Bypass","-File","..."]` or `curl.exe`. http hooks avoid all shell issues -> preferred on Windows.
- PTY: docs do not mention ConPTY/PTY requirements for hosting `claude`; it is a native exe (Node not needed even for npm install) using a TUI (alternate-screen "fullscreen" renderer by default? CLAUDE_CODE_NO_FLICKER=1 enables fullscreen; CLAUDE_CODE_DISABLE_ALTERNATE_SCREEN=1 = classic renderer). Hosting in ConPTY is standard practice but UNVERIFIED in docs (VS Code/JetBrains/Desktop host it themselves). Verify terminal-config page (https://code.claude.com/docs/en/terminal-config - NOT read) for TERM/Shift+Enter/ keybinding needs.
- Paths in hook payloads use backslashes (file_path in Write/Edit/Read). Transcript dir name for `C:\Users\Bohdan\Work\...` = `C--Users-Bohdan-Work-...` (':' and '\' -> '-').
- --settings: prefer file path; inline JSON needs `\"` escaping and differs between cmd, PowerShell 5.1 and Start-Process/CreateProcess arg quoting. When using ProcessBuilder/ConPTY with an argv array there is no shell, but the Windows command-line round-trip still mangles quotes -> use file.
- Installed binary: `%USERPROFILE%\.local\bin\claude.exe` (native) - may not be on PATH inside a GUI-spawned process; resolve absolute path yourself.
- Reading transcripts: Windows path-encoding and CRLF not an issue (JSONL written LF; UNVERIFIED).
- Env: Hooks inherit your process env; pass CCM_* variables in the spawn env and list them under allowedEnvVars for header interpolation.

---------------------------------------------------------------------------
## Not read / UNVERIFIED summary
- /monitoring-usage (OTel events - alternative full-fidelity telemetry: OTLP exporter via CLAUDE_CODE_ENABLE_TELEMETRY + OTEL_* env; env OTEL_* is scrubbed from hooks, not from the claude process; could be a second channel incl. tool results/cost per prompt).
- /terminal-config, /agent-sdk/typescript (exact stream-json schemas, control requests incl. canUseTool), /agent-view (background sessions: `claude --bg`, jobs/daemon state) - agent view is a competing native "dashboard" feature; `claude agents --json` is the supported machine-readable listing.
- Second half of /env-vars (chars 100000+), tail of /sub-agents, hooks page tail after prompt-hook section (hook debugging details, plugin hooks).
- Whether --settings hooks merge additively with user hooks (strongly implied, not shown by example), http hook `async`, PermissionRequest for ExitPlanMode/AskUserQuestion, `sessions/` file format, interrupt marker in transcript.

---------------------------------------------------------------------------
## 10. Verified by smoke test (claude 2.1.310, Windows 11, 2026-10-24)

Test: `ClaudeHookSmokeTest` (env `CCM_SMOKE=true`, 2 real `claude -p ... --model haiku --settings <file>` runs: a plain "ok" prompt and a prompt that runs `echo hi` through Bash). The settings file was produced by `HookSettingsWriter` (http handlers, timeout 5; SessionStart = async exec-form `curl.exe`), the receiver was `HookServer` answering 200 with an empty body.

Arrived, in order: InstructionsLoaded, SessionStart, UserPromptSubmit, (PreToolUse, PostToolUse, PostToolBatch for the Bash run), Stop, SessionEnd.
- SessionStart via async exec-form `curl` command handler works (http is documented as rejected for it; not tried). The posted body is the full hook JSON (curl `--data-binary @-` reads stdin). In `-p` the payload had only `session_id, transcript_path, cwd, hook_event_name, source` - no `model`, no `session_title`.
- http hooks on all other events work with an empty 200; Claude printed nothing extra and the result was unchanged. Hooks never blocked or delayed the run (a few seconds total).
- `--settings` hooks are ADDITIVE: a hook in the project's `.claude/settings.json` (UserPromptSubmit, shell form) still fired (marker file written) while the `--settings` hooks fired. The user-level `~/.claude/settings.json` of the test machine has no hooks, so user-level additivity was not exercised directly (same merge rule, expected to behave the same).
- `permission_mode` was `auto` in `-p` on this machine; `effort` appears only on tool/Stop events; `prompt_id` is present from UserPromptSubmit on, absent on SessionStart and InstructionsLoaded.
- InstructionsLoaded fires before SessionStart (the user's global CLAUDE.md), so a session can see events before its SessionStart.
- PostToolUse carries the whole `tool_response` (stdout, stderr, flags) and `duration_ms`; PostToolBatch repeats tool inputs/outputs. Payloads can be large: the store truncates every string to 500 chars and the server drops bodies over 1 MiB.
- Stop carries `last_assistant_message`, `background_tasks: []`, `session_crons: []`; SessionEnd reason was `other` at normal `-p` exit.
- `claude -p` does not exercise: permission prompts (PermissionRequest/Notification), AskUserQuestion/ExitPlanMode, subagents (SubagentStart/Stop), compaction, StopFailure, Esc interrupt, /clear. Their handling is implemented from the documented payloads and covered by unit tests only.
