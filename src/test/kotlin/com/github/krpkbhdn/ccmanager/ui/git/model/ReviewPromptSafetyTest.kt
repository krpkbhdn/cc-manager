package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewPromptSafetyTest {
    private fun comment(snippet: List<String>, text: String, id: String = "c") =
        ReviewComment(id, "a.kt", 1..1, false, snippet, emptyList(), text)

    private fun session(status: AgentStatus) =
        SessionSnapshot("s", 1, "claude", status, null, null, null, null, Instant.now(), Instant.now(), emptyList(), 0, null)

    @Test
    fun `crlf snippet and control characters never leave a raw CR or ESC in the prompt`() {
        val prompt = reviewPrompt(listOf(comment(listOf("+a\r\n", "+b\u001B[31mred\r"), "x\r\ny\u001Bz\u0007w")))

        assertThat(prompt).doesNotContain("\r").doesNotContain("\u001B").doesNotContain("\u0007")
    }

    @Test
    fun `snippet containing a code fence gets a longer fence so it stays intact`() {
        val prompt = reviewPrompt(listOf(comment(listOf("+```kotlin", "+val x = 1", "+```"), "t")))

        assertThat(prompt).contains("````diff\n+```kotlin\n+val x = 1\n+```\n````\nComment: t")
    }

    @Test
    fun `turnEnded is false without a sent comment`() {
        val review = ReviewComments()
        review.add(comment(listOf("+a"), "t"))

        assertThat(review.turnEnded(mapOf("s" to AgentStatus.WORKING), listOf(session(AgentStatus.IDLE))) { emptyList() }).isFalse()
        assertThat(review.recheckOffered).isFalse()
    }

    @Test
    fun `turnEnded fires once for a finished turn and not again`() {
        val review = ReviewComments()
        val c = comment(listOf("+a"), "t")
        review.add(c)
        review.markSent(setOf(c.id), "s", 5L)
        val before = mapOf("s" to AgentStatus.WORKING)
        val now = listOf(session(AgentStatus.IDLE))

        assertThat(review.turnEnded(before, now) { emptyList() }).isTrue()
        assertThat(review.recheckOffered).isTrue()
        assertThat(review.turnEnded(before, now) { emptyList() }).isFalse()
    }
}
