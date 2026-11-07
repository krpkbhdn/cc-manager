package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UiPreferencesTest {
    @Test
    fun `toggled adds a missing item and removes a present one`() {
        assertThat(emptySet<Long>().toggled(1L)).containsExactly(1L)
        assertThat(setOf(1L, 2L).toggled(1L)).containsExactly(2L)
    }
}
