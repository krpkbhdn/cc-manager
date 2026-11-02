package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import java.awt.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalComposeUiApi::class)
class ReportingExceptionHandlersTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = AppState(PreviewWorld().backend(PreviewTerminal), scope, UiSettingsStore(null))
    private val window = mock(Window::class.java)
    private val fallbackErrors = mutableListOf<Throwable>()
    private val fallback = WindowExceptionHandlerFactory { WindowExceptionHandler { fallbackErrors += it } }
    private var remounts = 0

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun handler(recovery: WindowRecovery) =
        reportingExceptionHandlers(state, fallback, recovery) { remounts++ }.exceptionHandler(window)

    @Test
    fun `a recoverable error remounts the window and shows one toast per distinct message`() {
        var now = 0L
        val handler = handler(WindowRecovery(clock = { now }, minIntervalMillis = 10L))

        handler.onException(IllegalStateException("boom"))
        now += 10L
        handler.onException(IllegalStateException("boom"))

        assertThat(remounts).isEqualTo(2)
        assertThat(state.notifications.toasts).hasSize(1)
        assertThat(fallbackErrors).isEmpty()
    }

    @Test
    fun `an error the recovery refuses goes to the default handler without a remount`() {
        val recovery = WindowRecovery(clock = { 0L })
        recovery.tryRecover()

        val error = IllegalStateException("again")
        handler(recovery).onException(error)

        assertThat(fallbackErrors).containsExactly(error)
        assertThat(remounts).isZero()
        assertThat(state.notifications.popups).isEmpty()
    }
}
