package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TaskbarAttentionTest {
    private val defaults = UiSettings()

    @Test
    fun `flashes only for an enabled kind while the visible window is not active`() {
        assertThat(shouldFlash(defaults, NotificationKind.NEEDS_ACTION, windowActive = false, windowVisible = true)).isTrue()
        assertThat(shouldFlash(defaults, NotificationKind.NEEDS_ACTION, windowActive = true, windowVisible = true)).isFalse()
        assertThat(shouldFlash(defaults, NotificationKind.NEEDS_ACTION, windowActive = false, windowVisible = false)).isFalse()
        assertThat(shouldFlash(defaults, NotificationKind.FINISHED, windowActive = false, windowVisible = true)).isTrue()
        assertThat(shouldFlash(defaults, NotificationKind.ERROR, windowActive = false, windowVisible = true)).isTrue()
        assertThat(shouldFlash(defaults.copy(osNotifyFinished = false), NotificationKind.FINISHED, windowActive = false, windowVisible = true)).isFalse()
        assertThat(shouldFlash(defaults.copy(doNotDisturb = true), NotificationKind.ERROR, windowActive = false, windowVisible = true)).isFalse()
    }

    @Test
    fun `badge is absent without waiting sessions and capped at nine`() {
        assertThat(taskbarBadge(0)).isNull()
        assertThat(taskbarBadge(3)).isEqualTo("3")
        assertThat(taskbarBadge(9)).isEqualTo("9")
        assertThat(taskbarBadge(10)).isEqualTo("9+")
    }
}
