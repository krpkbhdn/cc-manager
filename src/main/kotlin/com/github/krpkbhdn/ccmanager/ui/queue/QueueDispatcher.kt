package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus

enum class QueueDecision { SEND, WAIT }

/** What is known about one session at one moment; [status] is null when the session is gone. */
class QueueObservation(val status: AgentStatus?, val interrupted: Boolean, val queueEmpty: Boolean, val draftExists: Boolean, val blockedReason: String?)

/**
 * Decides when the next queued prompt may be typed. A session that is IDLE with a queued prompt is armed at once, whether the prompt was
 * added while idle or a turn just ended. An Esc interrupt holds it until the user adds a prompt, and a sent prompt holds it until the next
 * turn starts, so one dispatch sends one prompt. An armed session sends once no unsent user draft may exist and nothing blocks it; any
 * other status or an empty queue disarms it.
 */
class QueueDispatcher {
    private class Track(var previous: AgentStatus?, var armed: Boolean, var heldByInterrupt: Boolean = false, var awaitingTurn: Boolean = false)

    private val tracks = mutableMapOf<String, Track>()

    fun observe(ccmSessionId: String, observation: QueueObservation): QueueDecision {
        val status = observation.status
        if (status == null || status == AgentStatus.OFFLINE) {
            tracks.remove(ccmSessionId)
            return QueueDecision.WAIT
        }
        val track = tracks.getOrPut(ccmSessionId) { Track(null, false) }
        if (status != AgentStatus.IDLE) {
            track.armed = false
            track.heldByInterrupt = false
            track.awaitingTurn = false
        } else {
            if (track.previous == AgentStatus.WORKING) track.heldByInterrupt = observation.interrupted
            if (!track.heldByInterrupt && !track.awaitingTurn && !observation.queueEmpty) track.armed = true
        }
        track.previous = status
        if (observation.queueEmpty) track.armed = false
        val ready = track.armed && !observation.draftExists && observation.blockedReason == null
        return if (ready) QueueDecision.SEND else QueueDecision.WAIT
    }

    /** True while a queued prompt is waiting for the user's draft to be sent or cleared, or for the session to be free. */
    fun isWaiting(ccmSessionId: String): Boolean = tracks[ccmSessionId]?.armed == true

    /** The user added a prompt: an idle session after an Esc interrupt may send it. */
    fun promptAdded(ccmSessionId: String) {
        tracks[ccmSessionId]?.heldByInterrupt = false
    }

    /** The prompt of the armed session was typed; nothing more is sent before its next turn starts. */
    fun sent(ccmSessionId: String) {
        tracks[ccmSessionId]?.let { it.armed = false; it.awaitingTurn = true }
    }

    /** Forgets the sessions that are no longer live. */
    fun retainSessions(live: Set<String>) {
        tracks.keys.retainAll(live)
    }
}
