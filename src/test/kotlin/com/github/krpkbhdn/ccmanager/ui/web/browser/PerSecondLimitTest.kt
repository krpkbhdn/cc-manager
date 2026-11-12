package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class PerSecondLimitTest {
    private var clock = 0L
    private val limit = PerSecondLimit(3) { clock }

    @Test
    fun allows_the_budget_then_refuses() {
        val results = List(5) { limit.tryAcquire() }

        assertThat(results).containsExactly(true, true, true, false, false)
    }

    @Test
    fun the_budget_is_renewed_every_second() {
        repeat(5) { limit.tryAcquire() }
        clock += TimeUnit.SECONDS.toNanos(1)

        assertThat(limit.tryAcquire()).isTrue()
    }
}
