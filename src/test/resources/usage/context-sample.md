## Context Usage

**Model:** claude-sonnet-5-5  
**Tokens:** 96.4k / 1m (10%)

### Estimated usage by category

| Category | Tokens | Percentage |
|----------|--------|------------|
| System prompt | 3.8k | 0.4% |
| System tools | 9.2k | 0.9% |
| MCP tools | 599 | 0.1% |
| MCP server instructions | 952 | 0.1% |
| MCP tools (deferred) | 10.5k | 1.1% |
| System tools (deferred) | 15.3k | 1.5% |
| Custom agents | 314 | 0.0% |
| Memory files | 12.3k | 1.2% |
| Skills | 8.3k | 0.8% |
| Messages | 61.1k | 6.1% |
| Free space | 870.6k | 87.1% |
| Autocompact buffer | 33k | 3.3% |

### MCP Tools

| Tool | Server | Tokens |
|------|--------|--------|
| mcp__claude_ai_Claude_Docs__batch | claude_ai_Claude_Docs | 147 |
| mcp__claude_ai_Claude_Docs__create | claude_ai_Claude_Docs | 228 |
| mcp__claude_ai_Claude_Docs__delete | claude_ai_Claude_Docs | 296 |
| mcp__claude_ai_Claude_Docs__export | claude_ai_Claude_Docs | 449 |
| mcp__claude_ai_Claude_Docs__guide | claude_ai_Claude_Docs | 186 |
| mcp__claude_ai_Claude_Docs__query | claude_ai_Claude_Docs | 162 |
| mcp__claude_ai_Claude_Docs__read | claude_ai_Claude_Docs | 290 |
| mcp__claude_ai_Claude_Docs__update | claude_ai_Claude_Docs | 266 |
| mcp__plugin_context7_context7__query-docs | plugin_context7_context7 | 779 |
| mcp__plugin_context7_context7__resolve-library-id | plugin_context7_context7 | 1.1k |
| mcp__plugin_playwright_playwright__browser_click | plugin_playwright_playwright | 333 |
| mcp__plugin_playwright_playwright__browser_close | plugin_playwright_playwright | 84 |
| mcp__plugin_playwright_playwright__browser_console_messages | plugin_playwright_playwright | 318 |
| mcp__plugin_playwright_playwright__browser_drag | plugin_playwright_playwright | 320 |
| mcp__plugin_playwright_playwright__browser_drop | plugin_playwright_playwright | 374 |
| mcp__plugin_playwright_playwright__browser_emulate_media | plugin_playwright_playwright | 420 |
| mcp__plugin_playwright_playwright__browser_evaluate | plugin_playwright_playwright | 320 |
| mcp__plugin_playwright_playwright__browser_file_upload | plugin_playwright_playwright | 162 |
| mcp__plugin_playwright_playwright__browser_fill_form | plugin_playwright_playwright | 408 |
| mcp__plugin_playwright_playwright__browser_find | plugin_playwright_playwright | 476 |
| mcp__plugin_playwright_playwright__browser_handle_dialog | plugin_playwright_playwright | 161 |
| mcp__plugin_playwright_playwright__browser_hover | plugin_playwright_playwright | 195 |
| mcp__plugin_playwright_playwright__browser_navigate | plugin_playwright_playwright | 117 |
| mcp__plugin_playwright_playwright__browser_navigate_back | plugin_playwright_playwright | 99 |
| mcp__plugin_playwright_playwright__browser_network_request | plugin_playwright_playwright | 342 |
| mcp__plugin_playwright_playwright__browser_network_requests | plugin_playwright_playwright | 380 |
| mcp__plugin_playwright_playwright__browser_press_key | plugin_playwright_playwright | 143 |
| mcp__plugin_playwright_playwright__browser_resize | plugin_playwright_playwright | 153 |
| mcp__plugin_playwright_playwright__browser_run_code_unsafe | plugin_playwright_playwright | 324 |
| mcp__plugin_playwright_playwright__browser_select_option | plugin_playwright_playwright | 263 |
| mcp__plugin_playwright_playwright__browser_snapshot | plugin_playwright_playwright | 329 |
| mcp__plugin_playwright_playwright__browser_tabs | plugin_playwright_playwright | 336 |
| mcp__plugin_playwright_playwright__browser_take_screenshot | plugin_playwright_playwright | 584 |
| mcp__plugin_playwright_playwright__browser_type | plugin_playwright_playwright | 329 |
| mcp__plugin_playwright_playwright__browser_wait_for | plugin_playwright_playwright | 255 |

### Custom Agents

| Agent Type | Source | Tokens |
|------------|--------|--------|
| code-simplifier:code-simplifier | Plugin | 73 |
| feature-dev:code-architect | Plugin | 82 |
| feature-dev:code-explorer | Plugin | 76 |
| feature-dev:code-reviewer | Plugin | 83 |

### Memory Files

| Type | Path | Tokens |
|------|------|--------|
| User | C:\Users\Bohdan\.claude\CLAUDE.md | 708 |
| Project | C:\Users\Bohdan\Work\Code\Personal\cc-manager\.claude\worktrees\context-usage\CLAUDE.md | 11.4k |
| AutoMem | C:\Users\Bohdan\.claude\projects\C--Users-Bohdan-Work-Code-Personal-cc-manager\memory\MEMORY.md | 152 |

### Skills

| Skill | Source | Tokens |
|-------|--------|--------|
| dry-refactoring | User | ~90 |
| graphify | User | ~120 |
| jscpd | User | ~60 |
| claude-md-management:revise-claude-md | Plugin (claude-md-management) | ~30 |
| code-review:code-review | Plugin (code-review) | < 20 |
| feature-dev:feature-dev | Plugin (feature-dev) | ~40 |
| frontend-design:frontend-design | Plugin (frontend-design) | ~80 |
| claude-md-management:claude-md-improver | Plugin (claude-md-management) | ~130 |
| postman:ai-readiness | Plugin (postman) | ~110 |
| postman:api-discovery | Plugin (postman) | ~120 |
| postman:api-documentation | Plugin (postman) | ~110 |
| postman:api-engineer | Plugin (postman) | ~110 |
| postman:api-mocking | Plugin (postman) | ~200 |
| postman:api-monitoring | Plugin (postman) | ~120 |
| postman:api-simulation | Plugin (postman) | ~340 |
| postman:api-testing | Plugin (postman) | ~100 |
| postman:application-testing | Plugin (postman) | ~310 |
| postman:ci-integration | Plugin (postman) | ~110 |
| postman:cli-install | Plugin (postman) | ~100 |
| postman:collection-schema-v3 | Plugin (postman) | ~110 |
| postman:datasets | Plugin (postman) | ~140 |
| postman:flows | Plugin (postman) | ~110 |
| postman:performance-testing | Plugin (postman) | ~180 |
| postman:postman-mcp-server | Plugin (postman) | ~120 |
| postman:variables | Plugin (postman) | ~110 |
| postman:webhook-testing | Plugin (postman) | ~210 |
| postman:workspaces | Plugin (postman) | ~160 |
| ponytail:ponytail | Plugin (ponytail) | ~130 |
| ponytail:ponytail-audit | Plugin (ponytail) | ~120 |
| ponytail:ponytail-debt | Plugin (ponytail) | ~80 |
| ponytail:ponytail-gain | Plugin (ponytail) | ~60 |
| ponytail:ponytail-help | Plugin (ponytail) | ~60 |
| ponytail:ponytail-review | Plugin (ponytail) | ~130 |
| dataviz | Built-in | ~480 |
| update-config | Built-in | ~240 |
| keybindings-help | Built-in | ~80 |
| code-review | Built-in | ~280 |
| simplify | Built-in | ~60 |
| fewer-permission-prompts | Built-in | ~60 |
| loop | Built-in | ~120 |
| schedule | Built-in | ~130 |
| claude-api | Built-in | ~360 |
| workflow-authoring | Built-in | ~80 |
| run | Built-in | ~120 |
| plugin-authoring | Built-in | ~90 |
| init | Built-in | ~20 |
| security-review | Built-in | ~30 |
| docs | claude.ai sync | ~340 |
| docx | claude.ai sync | ~320 |
| google-workspace | claude.ai sync | ~330 |
| import-memory | claude.ai sync | ~60 |
| morning | claude.ai sync | ~120 |
| pdf | claude.ai sync | ~150 |
| pptx | claude.ai sync | ~330 |
| skill-creator | claude.ai sync | ~120 |
| xlsx | claude.ai sync | ~320 |

