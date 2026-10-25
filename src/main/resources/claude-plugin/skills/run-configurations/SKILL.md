---
name: run-configurations
description: Register and clean up cc-manager run configurations so the user can start the app from Claude Code Manager. Use when you start working in a git worktree, when you finish, merge or remove a worktree, or when the user asks how to run or test the app from cc-manager.
---

# Run configurations in cc-manager

This session runs inside Claude Code Manager. Its `cc-manager` MCP server has three tools: `list_run_configurations`, `set_run_configuration`, `delete_run_configuration`.

## Register

When you work in a git worktree, or the user asks to make the app runnable, call `set_run_configuration` once the change is runnable:

- `name`: `<app> (<branch or worktree name>)`.
- `command`: the command that starts the app. Find it in the project: build files, README, CLAUDE.md, package.json scripts.
- `directory`: the absolute path of the worktree.

Then tell the user they can start it with the Run button in cc-manager.

Call `list_run_configurations` first and reuse the same name instead of creating duplicates.

## Clean up

When the worktree is merged or removed, or the user says the work is done, call `delete_run_configuration` for every configuration whose `directory` is that worktree. Do not delete configurations of the main project folder unless asked.

## Command rules

- It runs in bash (Git Bash on Windows) from `directory`: use relative paths and no interactive prompts.
- Prefer the project's own wrapper: `./gradlew run`, `npm run dev`, and so on.
- Do not start the app yourself for the user's manual test: cc-manager does that.
