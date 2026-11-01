package com.github.krpkbhdn.ccmanager.ui.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SettingsStateTest {
    @Test
    fun `categories keep the order of the category list`() {
        assertThat(SettingsCategory.entries.map { it.label }).containsExactly(
            "Appearance", "Notifications", "Sessions", "Assistant", "Voice input", "Updates", "Author configuration", "About",
        )
    }

    @Test
    fun `stepping stops at both ends of the list`() {
        assertThat(SettingsCategory.APPEARANCE.step(-1)).isEqualTo(SettingsCategory.APPEARANCE)
        assertThat(SettingsCategory.ABOUT.step(1)).isEqualTo(SettingsCategory.ABOUT)
        assertThat(SettingsCategory.SESSIONS.step(1)).isEqualTo(SettingsCategory.ASSISTANT)
    }

    @Test
    fun `the selected category survives closing and reopening`() {
        val state = SettingsState()
        state.show()
        state.select(SettingsCategory.UPDATES)
        state.close()
        state.show()

        assertThat(state.open).isTrue()
        assertThat(state.category).isEqualTo(SettingsCategory.UPDATES)
    }

    @Test
    fun `moving changes the selected category`() {
        val state = SettingsState()
        state.move(2)
        assertThat(state.category).isEqualTo(SettingsCategory.SESSIONS)
    }
}
