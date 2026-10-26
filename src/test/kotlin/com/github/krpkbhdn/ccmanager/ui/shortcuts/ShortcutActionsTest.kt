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
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

    enum class Modal(val open: ShortcutActionsTest.() -> Unit) {
        CONFIRMATION({ state.confirm = confirmation() }),
        FOLDER_PICKER({ state.projects.pickerOpen = true }),
        TASK_DIALOG({ state.taskDialogProjectId = 1L }),
        RUN_CONFIGURATION({
            runBlocking { state.projects.load() }
            state.runs.openDialog(state.projects.all.first().id)
            assertThat(state.runs.dialog).isNotNull()
        }),
    }

    @ParameterizedTest
    @EnumSource(Modal::class)
    fun `every open modal blocks shortcuts`(modal: Modal) {
        modal.open(this)

        assertThat(actions.perform(AppShortcut.PALETTE)).isFalse()
        assertThat(state.paletteOpen).isFalse()
    }
}
