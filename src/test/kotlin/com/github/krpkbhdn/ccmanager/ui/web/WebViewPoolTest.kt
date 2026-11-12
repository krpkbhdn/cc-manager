package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import com.github.krpkbhdn.ccmanager.web.BrowserLimits
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebViewPoolTest {
    private class TestClock(var nowMillis: Long = 0) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }

    private val clock = TestClock()
    private val hosts = mutableMapOf<Long, FakePageHost>()
    private var opened = 0L
    private val pool = WebViewPool({ FakePageHost().also { hosts[++opened] = it } }, { BrowserLimits(2, Duration.ofMinutes(10)) }, clock, {})

    private fun open(projectId: Long) = pool.open(projectId).also { clock.nowMillis += 1 }

    @Test
    fun `the least recently shown hidden browser is closed past the limit`() {
        open(1)
        open(2)
        pool.shown(2)
        pool.hidden(2)

        open(3)

        assertThat(pool.find(1)).isNull()
        assertThat(hosts.getValue(1).closed).isTrue()
        assertThat(pool.find(2)).isNotNull()
        assertThat(pool.find(3)).isNotNull()
    }

    @Test
    fun `a shown browser and one with an unsent draft are never evicted`() {
        open(1)
        pool.shown(1)
        open(2).comment.setTextAndPlaceCursorAtEnd("unsent")

        open(3)

        assertThat(pool.find(1)).isNotNull()
        assertThat(pool.find(2)).isNotNull()
        assertThat(hosts.values.none { it.closed }).isTrue()
    }

    @Test
    fun `a browser hidden longer than the idle time is closed and can be reopened`() {
        open(1)
        pool.shown(1)
        pool.hidden(1)
        clock.nowMillis += Duration.ofMinutes(9).toMillis()
        pool.closeIdle()
        assertThat(pool.find(1)).isNotNull()

        clock.nowMillis += Duration.ofMinutes(2).toMillis()
        pool.closeIdle()

        assertThat(pool.find(1)).isNull()
        assertThat(hosts.getValue(1).closed).isTrue()
        assertThat(pool.open(1)).isNotNull()
    }

    @Test
    fun `a project that is gone closes its browser`() {
        open(1)
        open(2)

        pool.retain(listOf(2L))

        assertThat(hosts.getValue(1).closed).isTrue()
        assertThat(pool.find(2)).isNotNull()
    }
}
