package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextAlertsTest {

    private fun view(percent: Double) = contextUsageView(usage("s", percent))

    @Test
    fun `alerts only above the critical threshold`() {
        val alerts = ContextAlerts()

        assertThat(alerts.shouldAlert("s", view(70.0))).isFalse()
        assertThat(alerts.shouldAlert("s", view(74.0))).isTrue()
        assertThat(alerts.shouldAlert("s", null)).isFalse()
    }

    @Test
    fun `dismissed session stays quiet while critical`() {
        val alerts = ContextAlerts()
        alerts.dismiss("s")

        assertThat(alerts.shouldAlert("s", view(80.0))).isFalse()
        assertThat(alerts.shouldAlert("other", view(80.0))).isTrue()
    }

    @Test
    fun `dismissed session alerts again once its usage dropped, also for sessions not on screen`() {
        val alerts = ContextAlerts()
        alerts.dismiss("s")
        alerts.dismiss("t")

        alerts.rearmBelowCritical(mapOf("s" to usage("s", 80.0), "t" to usage("t", 20.0)))

        assertThat(alerts.shouldAlert("s", view(80.0))).isFalse()
        assertThat(alerts.shouldAlert("t", view(80.0))).isTrue()
    }

    @Test
    fun `dismissed session without usage is re-armed`() {
        val alerts = ContextAlerts()
        alerts.dismiss("s")

        alerts.rearmBelowCritical(emptyMap())

        assertThat(alerts.shouldAlert("s", view(80.0))).isTrue()
    }

    private fun usage(id: String, percent: Double) = SessionUsage(id, "Opus", "high", percent, null, null, Instant.EPOCH, null)
}
