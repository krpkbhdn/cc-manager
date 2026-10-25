## User setup (applies to `{{claudeConfigDir}}`)

1. Settings: read `{{claudeConfigDir}}/settings.json` (it may not exist). Merge the keys from `{{profileDir}}/user/settings-fragment.json` into it. If a key already has a different value, show both and ask which to keep. Propose the merged result and wait for the go-ahead before writing an existing file.
2. User CLAUDE.md: read `{{claudeConfigDir}}/CLAUDE.md` (it may not exist). Compare it with `{{profileDir}}/user/claude-md-rules.md`. If the file is missing, create it from the rules. Otherwise propose only the missing parts as an addition, and wait for the go-ahead before editing.
3. Skills: the bundled skills (jscpd, dry-refactoring) were copied by the app when they were missing. Check `{{claudeConfigDir}}/skills/` and report any that are absent; never overwrite one that exists.
4. graphify CLI: check whether `graphify` runs (`graphify --version`). If not, install it with `pip install graphifyy` (or `uv tool install graphifyy` / `pipx install graphifyy` when pip is unavailable or the environment is externally managed), then run `graphify install` to register the skill for Claude. Ask before installing if Python tooling is unusual on this machine.
5. jscpd CLI: check `jscpd --version`; if missing and Node.js with npm exists, run `npm i -g jscpd`. If Node.js is missing, do not install it; report it under Needs the user.
6. Plugins and marketplaces: the app installed them; if the pre-install report lists plugins needing confirmation or failures, tell the user to finish those on the Plugins page.
