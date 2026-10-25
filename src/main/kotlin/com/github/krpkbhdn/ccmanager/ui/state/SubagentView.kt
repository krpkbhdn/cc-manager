package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentState
import java.time.Duration
import java.time.Instant

private const val THINKING = "Thinking…"

/** Agents Claude still lists as running, in start order. */
fun SessionSnapshot.activeSubagents(): List<SubagentSnapshot> = subagents.filter { it.running() }

/** Closed agents, latest first. */
fun SessionSnapshot.finishedSubagents(): List<SubagentSnapshot> = subagents.filterNot { it.running() }.sortedByDescending { it.endedAt }

/** Same traffic light as a session: yellow while working, red while waiting for the user, hollow when silent for too long. */
fun SubagentSnapshot.dotStatus(): AgentStatus = when {
    stale -> AgentStatus.OFFLINE
    state == SubagentState.NEEDS_ACTION -> AgentStatus.NEEDS_ACTION
    else -> AgentStatus.WORKING
}

/** The tool being run, "Thinking…" between tools, or how long a silent agent has been quiet. */
fun SubagentSnapshot.liveLine(now: Instant): String = if (stale) "No activity for ${formatElapsed(silentFor(now))}" else activity.ifBlank { THINKING }

fun SubagentSnapshot.elapsed(now: Instant): Duration = Duration.between(startedAt, endedAt ?: now)

/** "12 tool calls · background" */
fun SubagentSnapshot.statsLine(): String =
    listOfNotNull("$toolCalls tool ${if (toolCalls == 1) "call" else "calls"}", "background".takeIf { background }).joinToString(" · ")

fun SubagentState.finalLabel(): String = when (this) {
    SubagentState.DONE -> "Done"
    SubagentState.FAILED -> "Failed"
    SubagentState.INTERRUPTED -> "Interrupted"
    SubagentState.WORKING -> "Working"
    SubagentState.NEEDS_ACTION -> "Needs action"
}

/** What a finished agent said last, or why there is nothing. */
fun SubagentSnapshot.summaryLine(): String = lastMessage.ifBlank { "No final message" }
