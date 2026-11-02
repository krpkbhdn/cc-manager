package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.await
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val RETRY_MILLIS = 20L

class QueueDispatchTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcherThread = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcherThread)
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
        executor.shutdownNow()
    }

    private fun session() = SessionSnapshot("s", 1, "claude", status, null, null, null, null, Instant.now(), Instant.now(), emptyList(), 0, null)

    private fun evaluate() = runBlocking(dispatcherThread) { dispatch.evaluate() }

    @Test
    fun `idle session gets the queued prompt once and it leaves the queue`() {
        queues.add("s", "first")

        evaluate()
        await { sent.isNotEmpty() }
        evaluate()

        assertThat(sent).containsExactly("first")
        assertThat(queues.of("s")).isEmpty()
    }

    @Test
    fun `a possible user draft holds the prompt and the timed re-check sends it once the draft is gone`() {
        draft = true
        queues.add("s", "later")

        evaluate()
        Thread.sleep(RETRY_MILLIS * 3)
        assertThat(sent).isEmpty()

        draft = false
        await { sent.isNotEmpty() }
        assertThat(sent).containsExactly("later")
    }

    @Test
    fun `the timed re-check gives up after the retry cap and the next evaluation restarts it`() {
        val capped = QueueDispatch(queues, { listOf(session()) }, { draft }, { false }, { _, text -> sent += text; true }, scope, clock::get, RETRY_MILLIS, 2)
        draft = true
        queues.add("s", "later")
        runBlocking(dispatcherThread) { capped.evaluate() }
        Thread.sleep(RETRY_MILLIS * 10)

        draft = false
        Thread.sleep(RETRY_MILLIS * 5)
        assertThat(sent).isEmpty()

        runBlocking(dispatcherThread) { capped.evaluate() }
        await { sent.isNotEmpty() }
        capped.dispose()
    }

    @Test
    fun `a sent prompt holds the next one until the session works or the await timeout passes`() {
        queues.add("s", "one")
        evaluate()
        await { sent.size == 1 }
        queues.add("s", "two")

        evaluate()
        assertThat(sent).containsExactly("one")

        clock.set(AWAIT_TURN_TIMEOUT_MILLIS)
        await { sent.size == 2 }
        assertThat(sent).containsExactly("one", "two")
    }
}
