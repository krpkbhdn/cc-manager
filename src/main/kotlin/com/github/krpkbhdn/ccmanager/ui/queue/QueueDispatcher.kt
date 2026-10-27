package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus

enum class QueueDecision { SEND, WAIT }

/** What is known about one session at one moment; [status] is null when the session is gone. */
class QueueObservation(val status: AgentStatus?, val interrupted: Boolean, val queueEmpty: Boolean, val draftExists: Boolean, val blockedReason: String?)

/**
 * Decides when the next queued prompt may be typed. A turn that ends (WORKING to IDLE) arms the session, an Esc interrupt does not
 * (the user stopped it on purpose); an armed session sends once no unsent user draft may exist and nothing blocks it, and is disarmed by
 * [sent], by any other status or by an empty queue, so one turn end sends one prompt.
 */
class QueueDispatcher {
    private class Track(var previous: AgentStatus?, var armed: Boolean)

    private val tracks = mutableMapOf<String, Track>()

    fun observe(ccmSessionId: String, observation: QueueObservation): QueueDecision {
        val status = observation.status
        if (status == null || status == AgentStatus.OFFLINE) {
            tracks.remove(ccmSessionId)
            return QueueDecision.WAIT
        }
        val track = tracks.getOrPut(ccmSessionId) { Track(null, false) }
        if (status != AgentStatus.IDLE) track.armed = false
        else if (track.previous == AgentStatus.WORKING) track.armed = !observation.interrupted
        track.previous = status
        if (observation.queueEmpty) track.armed = false
        val ready = track.armed && !observation.draftExists && observation.blockedReason == null
        return if (ready) QueueDecision.SEND else QueueDecision.WAIT
    }

    /** True while a turn end is waiting for the user's draft to be sent or cleared, or for the session to be free. */
    fun isWaiting(ccmSessionId: String): Boolean = tracks[ccmSessionId]?.armed == true

    /** The prompt of the armed turn end was typed. */
    fun sent(ccmSessionId: String) {
        tracks[ccmSessionId]?.armed = false
    }

    /** Forgets the sessions that are no longer live. */
    fun retainSessions(live: Set<String>) {
        tracks.keys.retainAll(live)
    }
}
