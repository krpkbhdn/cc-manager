@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val RETRY_MILLIS = 20L

class QueueDispatchTest {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val queues = PromptQueues()
    private val sent = CopyOnWriteArrayList<String>()
    private val clock = AtomicLong(0)
    @Volatile private var draft = false
    @Volatile private var status = AgentStatus.IDLE
    private val dispatch = QueueDispatch(
        queues, { listOf(session()) }, { draft }, { false }, { _, text -> sent += text; true }, scope, clock::get, RETRY_MILLIS,
    )

    @AfterEach
    fun stop() {
        dispatch.dispose()
        scope.cancel()
    }

    private fun session() = SessionSnapshot("s", 1, "claude", status, null, null, null, null, Instant.now(), Instant.now(), emptyList(), 0, null)

    private fun evaluate(target: QueueDispatch = dispatch) {
        target.evaluate()
        scheduler.runCurrent()
    }

    private fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    @Test
    fun `idle session gets the queued prompt once and it leaves the queue`() {
        queues.add("s", "first")

        evaluate()
        evaluate()

        assertThat(sent).containsExactly("first")
        assertThat(queues.of("s")).isEmpty()
    }

    @Test
    fun `a possible user draft holds the prompt and the timed re-check sends it once the draft is gone`() {
        draft = true
        queues.add("s", "later")

        evaluate()
        advance(RETRY_MILLIS * 3)
        assertThat(sent).isEmpty()

        draft = false
        advance(RETRY_MILLIS)
        assertThat(sent).containsExactly("later")
    }

    @Test
    fun `the timed re-check gives up after the retry cap and the next evaluation restarts it`() {
        val capped = QueueDispatch(queues, { listOf(session()) }, { draft }, { false }, { _, text -> sent += text; true }, scope, clock::get, RETRY_MILLIS, 2)
        draft = true
        queues.add("s", "later")
        evaluate(capped)
        advance(RETRY_MILLIS * 10)

        draft = false
        advance(RETRY_MILLIS * 5)
        assertThat(sent).isEmpty()

        evaluate(capped)
        assertThat(sent).isNotEmpty()
        capped.dispose()
    }

    @Test
    fun `a sent prompt holds the next one until the session works or the await timeout passes`() {
        queues.add("s", "one")
        evaluate()
        queues.add("s", "two")

        evaluate()
        assertThat(sent).containsExactly("one")

        clock.set(AWAIT_TURN_TIMEOUT_MILLIS)
        advance(RETRY_MILLIS)
        assertThat(sent).containsExactly("one", "two")
    }
}
