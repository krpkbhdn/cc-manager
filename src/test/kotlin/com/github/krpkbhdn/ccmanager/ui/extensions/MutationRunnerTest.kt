package com.github.krpkbhdn.ccmanager.ui.extensions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val WAIT_SECONDS = 5L

class MutationRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runner = MutationRunner(scope)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a refresh that began before a mutation is outdated once the mutation ran`() {
        val seen = runner.epoch
        val done = CountDownLatch(1)

        runner.run("Adding", "add", { 1 }) { done.countDown() }
        done.await(WAIT_SECONDS, TimeUnit.SECONDS)

        assertThat(runner.epoch).isGreaterThan(seen)
    }

    @Test
    fun `a second mutation is ignored while one runs`() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val ran = mutableListOf<String>()

        runner.run("First", "a", { release.await(WAIT_SECONDS, TimeUnit.SECONDS) }) { ran += "first"; finished.countDown() }
        runner.run("Second", "b", { 2 }) { ran += "second" }
        release.countDown()
        finished.await(WAIT_SECONDS, TimeUnit.SECONDS)

        assertThat(ran).containsExactly("first")
    }

    @Test
    fun `a failing mutation is kept as the failure and frees the runner`() {
        runner.run("Removing", "rm", { error("boom") })
        awaitIdle()

        assertThat(runner.failure?.title).isEqualTo("Removing")
        assertThat(runner.failure?.message).isEqualTo("boom")
    }

    private fun awaitIdle() {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS)
        while ((runner.job != null || runner.failure == null) && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }
}
