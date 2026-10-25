package com.github.krpkbhdn.ccmanager.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WindowRecoveryTest {
    private var now = 1_000L
    private val recovery = WindowRecovery({ now }, 10_000L)

    @Test
    fun theFirstFailureIsRecovered() {
        assertThat(recovery.tryRecover()).isTrue()
    }

    @Test
    fun aSecondFailureInsideTheIntervalIsNotRecovered() {
        recovery.tryRecover()
        now += 9_999

        assertThat(recovery.tryRecover()).isFalse()
    }

    @Test
    fun aFailureAfterTheIntervalIsRecoveredAgain() {
        recovery.tryRecover()
        now += 10_000

        assertThat(recovery.tryRecover()).isTrue()
    }
}
