package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.Review
import com.github.krpkbhdn.ccmanager.review.ReviewFeedback
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ReviewStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val service = mock(ReviewService::class.java)
    private val errors = CopyOnWriteArrayList<String>()
    private val enqueued = CopyOnWriteArrayList<Pair<String, String>>()
    private val started = CopyOnWriteArrayList<String>()
    private var liveSessions = LiveSessions()
    private val state = ReviewState(service, { liveSessions }, { id, text -> enqueued += id to text }, { _, prompt, _, _ -> started += prompt }, scope, { errors += it })
    private val mark = MarkDraft(MarkKind.RECTANGLE, 0.1, 0.2, 0.3, 0.4, "")

    @AfterEach
    fun stop() = scope.cancel()

    private fun review(id: Long, status: ReviewStatus, session: String? = null) =
        Review(id, 1, session, "r$id", "", status, Instant.ofEpochSecond(id), null)

    private fun snapshot(id: String) = mock(SessionSnapshot::class.java).also {
        `when`(it.ccmSessionId).thenReturn(id)
        `when`(it.projectId).thenReturn(1L)
        `when`(it.title).thenReturn("Session $id")
    }

    private fun feedback() = `when`(service.prepareFeedback(5)).thenReturn(ReviewFeedback(Path.of("review.md"), "Read review.md"))

    @Test
    fun `the newest pending review is selected by default and counted`() {
        `when`(service.list(1)).thenReturn(listOf(review(1, ReviewStatus.PENDING), review(2, ReviewStatus.APPROVED), review(3, ReviewStatus.PENDING)))
        state.start()
        await { state.reviews(1).size == 3 }

        assertThat(state.reviews(1).map { it.id }).containsExactly(3L, 2L, 1L)
        assertThat(state.selectedReview(1)!!.id).isEqualTo(3)
        assertThat(state.pendingCount(1)).isEqualTo(2)
        state.select(1, 1, 2)
        assertThat(state.selectedReview(1)!!.id).isEqualTo(1)
        assertThat(state.selectedImageIndex(1)).isEqualTo(2)
    }

    @Test
    fun `added, commented and deleted marks are saved debounced with the exact list`() {
        state.addMark(7, mark)
        state.addMark(7, MarkDraft(MarkKind.PIN, 0.5, 0.5, 0.0, 0.0, ""))
        state.setComment(7, 0, "too big")
        state.deleteMark(7, 1)
        val expected = MarkDraft(MarkKind.RECTANGLE, 0.1, 0.2, 0.3, 0.4, "too big")

        verify(service, timeout(3000)).saveMarks(7, listOf(expected))
        assertThat(state.marks(7)).containsExactly(expected)
    }

    @Test
    fun `an armed one-shot tool wins over the default and clears once a mark is added or on cancel`() {
        assertThat(state.tool).isEqualTo(ReviewTool.POINTER)
        state.arm(ReviewTool.RECTANGLE)
        assertThat(state.effectiveTool).isEqualTo(ReviewTool.RECTANGLE)
        state.addMark(7, mark)
        assertThat(state.oneShotTool).isNull()
        assertThat(state.effectiveTool).isEqualTo(ReviewTool.POINTER)

        state.arm(ReviewTool.PIN)
        state.cancel()
        assertThat(state.oneShotTool).isNull()
        assertThat(state.selectedMark).isEqualTo(0)
        assertThat(state.cancel()).isTrue()
        assertThat(state.selectedMark).isNull()
        assertThat(state.cancel()).isFalse()
    }

    @Test
    fun `updating a mark keeps its comment`() {
        state.addMark(7, MarkDraft(MarkKind.RECTANGLE, 0.1, 0.2, 0.3, 0.4, "keep"))
        state.updateMark(7, 0, MarkDraft(MarkKind.RECTANGLE, 0.5, 0.5, 0.1, 0.1, ""))

        assertThat(state.marks(7)).containsExactly(MarkDraft(MarkKind.RECTANGLE, 0.5, 0.5, 0.1, 0.1, "keep"))
    }

    @Test
    fun `send to a live bound session enqueues the one-line prompt`() {
        feedback()
        liveSessions = LiveSessions(sessions = listOf(snapshot("s1")))

        state.send(review(5, ReviewStatus.PENDING, "s1"))

        await { enqueued.isNotEmpty() }
        assertThat(enqueued).containsExactly("s1" to "Read review.md")
        assertThat(state.sendTarget).isNull()
    }

    @Test
    fun `send with a dead session offers the live sessions of the project`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s2")))

        state.send(review(5, ReviewStatus.PENDING, "gone"))

        await { state.sendTarget != null }
        assertThat(state.sendTarget!!.candidates).containsExactly(SessionChoice("s2", "Session s2"))
        assertThat(enqueued).isEmpty()
    }

    @Test
    fun `sending to no session starts a new one`() {
        feedback()
        val review = review(5, ReviewStatus.PENDING)

        state.sendTo(review, null)

        await { started.isNotEmpty() }
        assertThat(started).containsExactly("Read review.md")
        assertThat(state.sendTarget).isNull()
    }

    @Test
    fun `approve settles the review without a prompt`() {
        state.approve(review(5, ReviewStatus.PENDING))

        verify(service, timeout(3000)).approve(5)
        assertThat(enqueued).isEmpty()
    }

    @Test
    fun `sending right after a mark edit saves the latest marks before the feedback is prepared`() {
        feedback()
        liveSessions = LiveSessions(sessions = listOf(snapshot("s1")))
        state.addMark(7, mark)
        state.setComment(7, 0, "latest")

        state.send(review(5, ReviewStatus.PENDING, "s1"))

        await { enqueued.isNotEmpty() }
        val order = inOrder(service)
        order.verify(service).saveMarks(7, listOf(MarkDraft(MarkKind.RECTANGLE, 0.1, 0.2, 0.3, 0.4, "latest")))
        order.verify(service).prepareFeedback(5)
    }

    @Test
    fun `dispose saves pending marks without waiting for the debounce`() {
        state.addMark(7, mark)

        state.dispose()

        verify(service).saveMarks(7, listOf(mark))
    }

    @Test
    fun `a failed save keeps the marks dirty so the next flush writes them again`() {
        doThrow(RuntimeException("disk full")).doNothing().`when`(service).saveMarks(7, listOf(mark))
        state.addMark(7, mark)

        state.dispose()
        assertThat(errors).containsExactly("disk full")
        state.dispose()

        verify(service, times(2)).saveMarks(7, listOf(mark))
    }

    @Test
    fun `a send to a session that ended meanwhile offers the chooser and keeps the review unsent`() {
        liveSessions = LiveSessions(sessions = listOf(snapshot("s2")))

        state.sendTo(review(5, ReviewStatus.PENDING), "gone")

        await { state.sendTarget != null }
        assertThat(state.sendTarget!!.candidates).containsExactly(SessionChoice("s2", "Session s2"))
        verify(service, never()).prepareFeedback(5)
    }

    @Test
    fun `a new session that does not start reports where the feedback file is`() {
        feedback()
        val failing = ReviewState(service, { liveSessions }, { _, _ -> }, { _, _, onNotStarted, _ -> onNotStarted() }, scope, { errors += it })

        failing.sendTo(review(5, ReviewStatus.PENDING), null)

        await { errors.isNotEmpty() }
        assertThat(errors).containsExactly("Could not start a session. Feedback is in review.md")
    }
}
