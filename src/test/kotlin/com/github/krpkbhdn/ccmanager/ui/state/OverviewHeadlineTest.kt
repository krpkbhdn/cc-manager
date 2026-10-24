package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OverviewHeadlineTest {
    @Test
    fun `attention wins over everything and is pluralised`() {
        assertThat(overviewHeadline(1, 3, 4).title).isEqualTo("1 session needs you")
        assertThat(overviewHeadline(2, 0, 2).title).isEqualTo("2 sessions need you")
    }

    @Test
    fun `working sessions come next`() {
        assertThat(overviewHeadline(0, 1, 1).title).isEqualTo("1 session is working")
        assertThat(overviewHeadline(0, 4, 5).title).isEqualTo("4 sessions are working")
    }

    @Test
    fun `calm states distinguish idle sessions from none`() {
        assertThat(overviewHeadline(0, 0, 2).title).isEqualTo("All sessions are free")
        assertThat(overviewHeadline(0, 0, 0).title).isEqualTo("Nothing running")
    }
}
