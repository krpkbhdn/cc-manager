package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.queue.PromptQueues
import com.github.krpkbhdn.ccmanager.ui.queue.QueueDispatch
import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ReviewSendFlowTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val thread = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private val queues = PromptQueues()
    private val sent = CopyOnWriteArrayList<String>()
    @Volatile private var draft = false
    @Volatile private var status = AgentStatus.IDLE
    private val dispatch = QueueDispatch(queues, { listOf(session()) }, { draft }, { false }, { _, text -> sent += text; true }, scope)
    private val review = ReviewComments()

    @AfterEach
    fun stop() {
        dispatch.dispose()
        scope.cancel()
        executor.shutdownNow()
    }

    private fun session() = SessionSnapshot("s", 1, "claude", status, null, null, null, null, Instant.now(), Instant.now(), emptyList(), 0, null)

    private fun comment(path: String, line: Int, code: String, text: String) =
        reviewCommentOf(path, listOf(DiffRow(Line(LineKind.ADDED, code, null, line))), 0..0, text)

    private fun addThree() {
        review.add(comment("A.kt", 3, "val a = 1", "rename a"))
        review.add(comment("B.kt", 7, "val b = 2", "why b"))
        review.add(comment("C.kt", 11, "val c = 3", "drop c"))
    }

    /** Mirrors what the panel's Send button does. */
    private fun sendOpen() {
        val open = review.open()
        if (open.isEmpty()) return
        val id = queues.add("s", reviewPrompt(open))
        dispatch.promptAdded("s")
        review.markSent(open.map { it.id }.toSet(), "s", id)
        runBlocking(thread) { dispatch.evaluate() }
    }

    @Test
    fun `three comments on three files become one prompt with all refs snippets and texts and are marked sent`() {
        addThree()

        sendOpen()
        await { sent.isNotEmpty() }

        assertThat(sent).hasSize(1)
        assertThat(sent[0]).contains("A.kt:3", "B.kt:7", "C.kt:11", "+val a = 1", "+val b = 2", "+val c = 3", "rename a", "why b", "drop c")
        assertThat(review.all.map { it.status }).containsOnly(ReviewStatus.SENT)
        assertThat(review.open()).isEmpty()
    }

    @Test
    fun `no open comments sends nothing`() {
        sendOpen()
        addThree()
        sendOpen()
        sendOpen()

        await { sent.isNotEmpty() }
        assertThat(sent).hasSize(1)
    }

    @Test
    fun `a busy session or a possible draft holds the review prompt`() {
        status = AgentStatus.WORKING
        addThree()
        sendOpen()
        Thread.sleep(150)
        assertThat(sent).isEmpty()

        status = AgentStatus.IDLE
        draft = true
        runBlocking(thread) { dispatch.evaluate() }
        Thread.sleep(150)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `comment text with a blank body is not a prompt`() {
        assertThat(queues.of("s")).isEmpty()
        assertThat(reviewPrompt(emptyList())).doesNotContain("1. ")
    }

    @Test
    fun `removed snippet is stale and moved snippet is relocated`() {
        val moved = ReviewComment("m", "a.txt", 1..1, false, listOf("+x"), listOf("x"), "t")
        val haystack = ReviewHaystack(listOf("a", "b", "x"), listOf(1, 2, 3))
        val gone = moved.copy(id = "g", anchor = listOf("zzz"))
        review.add(moved)
        review.add(gone)

        review.recheck(mapOf("m" to haystack.locate(moved), "g" to haystack.locate(gone)))

        assertThat(review.all.first { it.id == "m" }.lines).isEqualTo(3..3)
        assertThat(review.all.first { it.id == "g" }.status).isEqualTo(ReviewStatus.STALE)
    }
}
