package com.github.krpkbhdn.ccmanager.ui

import java.util.concurrent.CountDownLatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShutdownStepTest {
    @Test
    fun `a failing step does not stop the next one`() {
        val ran = mutableListOf<String>()

        shutdownStep("first") { throw IllegalStateException("boom") }
        shutdownStep("second") { ran += "second" }

        assertThat(ran).containsExactly("second")
    }

    @Test
    fun `a hung step is left behind after its limit`() {
        val never = CountDownLatch(1)
        val started = System.nanoTime()

        shutdownStep("hung", 100) { never.await() }

        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5_000)
        never.countDown()
    }
}
