package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry

/** The subagent whose log is open; [entries] is null while the transcript loads. */
data class AgentLog(val ccmSessionId: String, val agent: SubagentSnapshot, val entries: List<TranscriptEntry>?)
