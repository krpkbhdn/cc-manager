package com.github.krpkbhdn.ccmanager.ui.git.changes

import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot

/** Where review comments go: the project's live [sessions], the [preferred] one's id and [send], which queues a prompt and returns its id, and [startAndSend], which launches a session with the prompt as its first one and reports its id once it started. */
class ReviewTarget(
    val sessions: () -> List<SessionSnapshot>,
    val preferred: () -> String?,
    val send: (String, String) -> Long?,
    val startAndSend: (prompt: String, onStarted: (ccmSessionId: String) -> Unit) -> Unit,
)
