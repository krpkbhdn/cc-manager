package com.github.krpkbhdn.ccmanager.ui.terminal

import java.io.File

internal const val SANDBOX_DIR = "C:/Users/Bohdan/Work/Code/Personal/ccm-sandbox/demo-api"
internal val CLAUDE_EXE: String = File(System.getProperty("user.home"), ".local/bin/claude.exe").path

private val POLLUTING_PREFIXES = (System.getenv("DEV_STRIP") ?: "NO_COLOR,CLAUDE,AI_AGENT,TERMINAL_EMULATOR,TERM_SESSION_ID,ANTHROPIC_").split(",").filter { it.isNotEmpty() }

/**
 * Parent environment without markers of the Claude/IDE session that runs the dev tools, so child claude behaves like a fresh launch.
 * CLAUDE_CODE_NO_FLICKER is set explicitly so short dev runs are not counted as fullscreen start failures that disable the fullscreen renderer.
 */
internal fun cleanDevEnvironment(): Map<String, String> =
    System.getenv().filterKeys { key -> POLLUTING_PREFIXES.none { key.startsWith(it) } } +
        mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor", "CLAUDE_CODE_NO_FLICKER" to "1")
