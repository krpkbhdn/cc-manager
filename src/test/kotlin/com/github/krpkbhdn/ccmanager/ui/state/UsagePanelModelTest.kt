package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsagePanelModelTest {
    private val now = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun windowDetailCarriesUsedLeftAndBothResetForms() {
        val snapshot = RateLimitSnapshot(RateLimitWindow(23.5, now.plusSeconds(2 * 3600 + 14 * 60)), null, now)

        val detail = usageWindowDetails(snapshot, now, ZoneOffset.UTC).single()

        assertThat(detail.title).isEqualTo("5-hour limit")
        assertThat(detail.usedText).isEqualTo("23.5% used")
        assertThat(detail.remainingText).isEqualTo("76.5% left")
        assertThat(detail.resetsInText).isEqualTo("resets in 2h 14m")
        assertThat(detail.resetsAtText).isEqualTo("Thu 1 Jan, 12:14")
        assertThat(detail.level).isEqualTo(UsageLevel.NORMAL)
    }

    @Test
    fun noSnapshotAndResetWindowsYieldNoDetails() {
        assertThat(usageWindowDetails(null, now)).isEmpty()
        assertThat(usageWindowDetails(RateLimitSnapshot(RateLimitWindow(50.0, now.minusSeconds(1)), null, now), now)).isEmpty()
    }

    @Test
    fun freshnessIsRelative() {
        assertThat(usageFreshness(RateLimitSnapshot(null, null, now.minusSeconds(180)), now)).isEqualTo("updated 3m ago")
    }

    @Test
    fun sessionRowsPreferReportedModelAndFormatCostAndContext() {
        val session = SessionSnapshot("s1", 1, "c1", AgentStatus.IDLE, "", "default", "claude-opus-4-7", null, now, now, emptyList(), 0, null)
        val usage = mapOf("s1" to SessionUsage("s1", "Opus 4.7", "high", 41.6, 1.5, 1000L, now))

        val row = sessionUsageRows(listOf(session), usage) { "proj" }.single()

        assertThat(row).isEqualTo(SessionUsageRow("s1", sessionName(session), "proj", AgentStatus.IDLE, "Opus 4.7", "high", 42, "$1.50"))
    }

    @Test
    fun sessionWithoutReportFallsBackToSnapshotModel() {
        val session = SessionSnapshot("s1", 1, "c1", AgentStatus.IDLE, "", "default", "claude-opus-4-7", null, now, now, emptyList(), 0, null)

        val row = sessionUsageRows(listOf(session), emptyMap()) { "proj" }.single()

        assertThat(row.model).isEqualTo("claude-opus-4-7")
        assertThat(row.contextPercent).isNull()
        assertThat(row.costText).isNull()
    }
}
