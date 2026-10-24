package com.github.krpkbhdn.ccmanager.ui.git.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitDatesTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")

    private fun age(duration: Duration) = commitAge(now.minus(duration), now)

    @Test
    fun `recent commits use the short relative form`() {
        assertThat(age(Duration.ofSeconds(3))).isEqualTo("just now")
        assertThat(age(Duration.ofMinutes(5))).isEqualTo("5m ago")
        assertThat(age(Duration.ofHours(3))).isEqualTo("3h ago")
        assertThat(age(Duration.ofDays(6))).isEqualTo("6d ago")
    }

    @Test
    fun `older commits count weeks months and years`() {
        assertThat(age(Duration.ofDays(7))).isEqualTo("1w ago")
        assertThat(age(Duration.ofDays(21))).isEqualTo("3w ago")
        assertThat(age(Duration.ofDays(60))).isEqualTo("2mo ago")
        assertThat(age(Duration.ofDays(800))).isEqualTo("2y ago")
    }

    @Test
    fun `absolute dates are minute precise in the given zone`() {
        assertThat(absoluteDate(Instant.parse("2026-01-05T09:07:30Z"), ZoneOffset.UTC)).isEqualTo("2026-01-05 09:07")
    }
}
