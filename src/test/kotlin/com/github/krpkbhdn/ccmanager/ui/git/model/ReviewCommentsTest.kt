package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewCommentsTest {
    private val comments = ReviewComments()

    private fun comment(id: String, path: String = "A.kt") =
        ReviewComment(id, path, 1..2, false, listOf("+a"), listOf("a"), "t")

    private fun session(status: AgentStatus, activity: String? = null) =
        SessionSnapshot("s1", 1L, null, status, activity, null, null, null, null, null, emptyList(), 0, null)

    private fun sent(promptId: Long? = 5L) {
        comments.add(comment("1"))
        comments.markSent(setOf("1"), "s1", promptId)
    }

    @Test
    fun `add remove open and forPath`() {
        comments.add(comment("1", "A.kt"))
        comments.add(comment("2", "B.kt"))
        comments.markSent(setOf("2"), "s1", null)

        assertThat(comments.open().map { it.id }).containsExactly("1")
        assertThat(comments.forPath("B.kt").map { it.id }).containsExactly("2")
        comments.remove("1")
        assertThat(comments.all.map { it.id }).containsExactly("2")
    }

    @Test
    fun `relocated and recheck move comments or mark them stale`() {
        comments.add(comment("1"))
        comments.add(comment("2"))
        comments.relocated("1", null)
        assertThat(comments.all[0].status).isEqualTo(ReviewStatus.STALE)

        comments.recheck(mapOf("1" to 8..9, "2" to null))
        assertThat(comments.all[0].lines).isEqualTo(8..9)
        assertThat(comments.all[0].status).isEqualTo(ReviewStatus.OPEN)
        assertThat(comments.all[1].status).isEqualTo(ReviewStatus.STALE)
    }

    @Test
    fun `turn end is offered when the target went working to idle and the prompt left the queue`() {
        sent()

        assertThat(comments.turnEnded(mapOf("s1" to AgentStatus.WORKING), listOf(session(AgentStatus.IDLE))) { emptyList() }).isTrue()
        assertThat(comments.recheckOffered).isTrue()
        comments.clearRecheckOffer()
        assertThat(comments.recheckOffered).isFalse()
    }

    @Test
    fun `turn end is not offered when interrupted, still queued, not a transition or nothing was sent`() {
        val working = mapOf("s1" to AgentStatus.WORKING)
        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()

        sent()
        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.IDLE, ActivityFormatter.INTERRUPTED))) { emptyList() }).isFalse()
        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.IDLE))) { listOf(5L) }).isFalse()
        assertThat(comments.turnEnded(mapOf("s1" to AgentStatus.IDLE), listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.WORKING))) { emptyList() }).isFalse()
        assertThat(comments.recheckOffered).isFalse()
    }

    @Test
    fun `a launched first prompt has no queue id and is offered after its first turn but not while idle before it`() {
        comments.add(comment("1"))
        comments.markSent(setOf("1"), "s1", null)

        assertThat(comments.turnEnded(emptyMap(), listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
        assertThat(comments.turnEnded(mapOf("s1" to AgentStatus.IDLE), listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
        assertThat(comments.turnEnded(mapOf("s1" to AgentStatus.WORKING), listOf(session(AgentStatus.IDLE))) { emptyList() }).isTrue()
    }

    @Test
    fun `offer is raised once and a later turn does not offer again`() {
        sent()
        val working = mapOf("s1" to AgentStatus.WORKING)

        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.IDLE))) { emptyList() }).isTrue()
        assertThat(comments.sentTo).isNull()
        assertThat(comments.turnEnded(working, listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
    }

    @Test
    fun `no offer once no sent comment remains`() {
        sent()
        comments.remove("1")

        assertThat(comments.sentTo).isNull()
        assertThat(comments.turnEnded(mapOf("s1" to AgentStatus.WORKING), listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
    }

    @Test
    fun `reopenSent makes sent comments open again and clears the target`() {
        sent()
        comments.reopenSent()

        assertThat(comments.open().map { it.id }).containsExactly("1")
        assertThat(comments.sentTo).isNull()
        assertThat(comments.promptId).isNull()
    }

    @Test
    fun `recheck clears the target`() {
        sent()
        comments.recheck(mapOf("1" to 1..2))

        assertThat(comments.sentTo).isNull()
        assertThat(comments.statusOf("1")).isEqualTo(ReviewStatus.OPEN)
    }
}
