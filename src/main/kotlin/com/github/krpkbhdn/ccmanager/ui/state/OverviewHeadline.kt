package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant

data class Headline(val title: String, val subtitle: String)

/**
 * Hero copy of the overview: attention first, then working sessions, then calm.
 * [answerable] counts the sessions whose question or approval can be answered in the app.
 */
fun overviewHeadline(attention: Int, working: Int, live: Int, answerable: Int = 0): Headline = when {
    attention > 0 -> Headline(
        if (attention == 1) "1 session needs you" else "$attention sessions need you",
        if (answerable > 0) "Claude is waiting on a decision. Answer it here, or jump into the terminal." else "Claude is waiting on a decision. Jump straight into the terminal.",
    )
    working > 0 -> Headline(
        if (working == 1) "1 session is working" else "$working sessions are working",
        "Nothing needs you right now. You will be told the moment something does.",
    )
    live > 0 -> Headline("All sessions are free", "Every agent is idle and ready for the next task.")
    else -> Headline("Nothing running", "Open a project and launch Claude to get started.")
}

/** Most recent hook event across [sessions], or null when none reported yet. */
fun lastActivityAt(sessions: List<SessionSnapshot>): Instant? = sessions.mapNotNull { it.lastEventAt }.maxOrNull()
