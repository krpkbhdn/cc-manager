package com.github.krpkbhdn.ccmanager.ui.shortcuts

import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ShortcutActionsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = AppState(PreviewWorld().backend(PreviewTerminal), scope, UiSettingsStore(null))
    private val actions = ShortcutActions(state) { false }

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun confirmation() = ConfirmRequest("Remove project", "sure?", "Remove") {}

    @Test
    fun `a shortcut acts when no dialog is open`() {
        assertThat(actions.perform(AppShortcut.PALETTE)).isTrue()

        assertThat(state.paletteOpen).isTrue()
    }

    @Test
    fun `an open confirmation blocks shortcuts`() {
        state.confirm = confirmation()

        assertThat(actions.perform(AppShortcut.PALETTE)).isFalse()
        assertThat(actions.perform(AppShortcut.TOGGLE_SIDEBAR)).isFalse()

        assertThat(state.paletteOpen).isFalse()
        assertThat(state.settings.sidebarCollapsed).isFalse()
    }

    @Test
    fun `the folder picker and the task dialog block shortcuts`() {
        state.projects.pickerOpen = true
        assertThat(actions.perform(AppShortcut.PALETTE)).isFalse()
        state.projects.pickerOpen = false

        state.taskDialogProjectId = 1L
        assertThat(actions.perform(AppShortcut.PALETTE)).isFalse()
        state.taskDialogProjectId = null

        assertThat(actions.perform(AppShortcut.PALETTE)).isTrue()
    }

    @Test
    fun `closing an overlay still works while a dialog is open`() {
        state.paletteOpen = true
        state.confirm = confirmation()

        assertThat(actions.perform(AppShortcut.CLOSE_OVERLAY)).isTrue()

        assertThat(state.paletteOpen).isFalse()
    }
}
