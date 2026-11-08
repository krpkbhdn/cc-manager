package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdownResult
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ContextBreakdownStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val measurements = AtomicInteger()
    private val release = CountDownLatch(1)
    private var result = ContextBreakdownResult.success(breakdown())
    private val target = ContextTarget(Path.of("work"), "claude-1")

    @AfterEach
    fun stop() = scope.cancel()

    private fun breakdown() = ContextBreakdown("Opus", 10, 100, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false, Instant.EPOCH, emptyList(), "raw")

    private fun state(blocking: Boolean = false, targetOf: (String) -> ContextTarget? = { target }) = ContextBreakdownState({ _, _ ->
        measurements.incrementAndGet()
        if (blocking) release.await()
        result
    }, scope, targetOf)

    @Test
    fun `loads a breakdown and clears the loading flag`() {
        val state = state()

        state.load("s", false)
        await { !state.isLoading("s") && state.loadOf("s") is BreakdownLoad.Done }

        assertThat(measurements.get()).isEqualTo(1)
    }

    @Test
    fun `a second load while one is in flight does not measure again`() {
        val state = state(blocking = true)

        state.load("s", false)
        state.load("s", true)
        assertThat(state.isLoading("s")).isTrue()
        release.countDown()
        await { !state.isLoading("s") }

        assertThat(measurements.get()).isEqualTo(1)
    }

    @Test
    fun `forced reload keeps the old result displayed but marks loading`() {
        val state = state()
        state.load("s", false)
        await { !state.isLoading("s") && state.loadOf("s") is BreakdownLoad.Done }
        val blocked = state(blocking = true)
        blocked.load("s", false)
        await { blocked.loadOf("s") is BreakdownLoad.Loading }
        release.countDown()
        await { !blocked.isLoading("s") }

        state.load("s", true)

        assertThat(state.isLoading("s")).isTrue()
        assertThat(state.loadOf("s")).isInstanceOf(BreakdownLoad.Done::class.java)
        await { !state.isLoading("s") }
        assertThat(measurements.get()).isEqualTo(3)
    }

    @Test
    fun `a failed measurement is shown with its reason`() {
        result = ContextBreakdownResult.failed("boom")
        val state = state()

        state.load("s", false)
        await { state.loadOf("s") is BreakdownLoad.Failed }

        assertThat((state.loadOf("s") as BreakdownLoad.Failed).reason).isEqualTo("boom")
    }

    @Test
    fun `a session without a target fails without measuring`() {
        val state = state(targetOf = { null })

        state.load("s", false)

        assertThat(state.loadOf("s")).isEqualTo(BreakdownLoad.Failed("The session is not running."))
        assertThat(measurements.get()).isZero()
    }
}
