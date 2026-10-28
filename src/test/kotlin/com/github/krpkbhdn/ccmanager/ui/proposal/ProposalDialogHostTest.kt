package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.shortcuts.ShortcutActions
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 6
private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 20L
private const val SESSION = "s-agents"

/** The dialog host against a whole AppState with fake services: what hides, replaces or closes a dialog, and where the focus goes. */
@OptIn(InternalComposeUiApi::class)
class ProposalDialogHostTest {
    private val focused = mutableListOf<String>()
    private val terminal = object : TerminalPort by PreviewTerminal {
        override fun focus(ccmSessionId: String) {
            focused += ccmSessionId
        }

        override fun open(session: LaunchedSession) = Unit
    }
    private val world = PreviewWorld()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var state: AppState
    private lateinit var scene: ImageComposeScene
    private var nanos = 0L

    @BeforeEach
    fun open() {
        state = AppState(world.backend(terminal), scope, UiSettingsStore(null)).also { it.updateSettings { s -> s.copy(assistantStartup = AssistantStartup.NEVER) }; it.start() }
        until { state.live.find(SESSION) != null }
        state.openSession(SESSION)
        scene = ImageComposeScene(1000, 800, Density(1f)) { host { ProposalDialogHost(state) } }
        until { state.proposalDialog != null }
        frames()
    }

    @AfterEach
    fun close() {
        scope.cancel()
        runCatching { scene.close() }
    }

    @Composable
    private fun host(content: @Composable () -> Unit) = AppTheme(dark = true, content = content)

    private fun frames() = repeat(SETTLE_FRAMES) { scene.render(nanos); nanos += FRAME_NANOS }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertThat(condition()).isTrue()
    }

    private fun press(key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        frames()
    }

    private fun secondProposal() = PermissionProposal("p2", SESSION, Instant.now(), "", "Bash", JsonMapper.builder().build().readTree("""{"command":"ls"}"""), emptyList())

    @Test
    fun escapeHidesTheDialogAndKeepsTheProposalPending() {
        press(Key.Escape)

        assertThat(state.proposalDialog).isNull()
        assertThat(state.proposals.of(SESSION).map { it.id() }).contains("q1")
        assertThat(state.proposals.hiddenCount(SESSION)).isEqualTo(1)
        assertThat(world.proposals.service().pending().map { it.id() }).contains("q1")
    }

    @Test
    fun reopeningShowsTheHiddenDialogAgain() {
        press(Key.Escape)
        state.proposals.reopen(SESSION)

        assertThat(state.proposalDialog?.id()).isEqualTo("q1")
    }

    @Test
    fun aProposalSettledElsewhereClosesItsDialogAndTheNextPendingOneTakesOver() {
        world.proposals.add(secondProposal())
        until { state.proposals.of(SESSION).size == 2 }
        focused.clear()

        world.proposals.settle("q1")
        until { state.proposalDialog?.id() == "p2" }
        frames()

        assertThat(state.proposals.positionOf(state.proposalDialog!!)).isNull()
    }

    @Test
    fun whenNothingFollowsASettledDialogTheKeyboardGoesBackToTheTerminal() {
        focused.clear()

        world.proposals.settle("q1")
        until { state.proposalDialog == null }
        frames()

        assertThat(focused).contains(SESSION)
    }

    @Test
    fun globalShortcutsAreBlockedWhileAProposalDialogIsShown() {
        val actions = ShortcutActions(state) { true }
        val collapsed = state.settings.sidebarCollapsed

        assertThat(actions.perform(AppShortcut.TOGGLE_SIDEBAR)).isFalse()
        assertThat(state.settings.sidebarCollapsed).isEqualTo(collapsed)
        world.proposals.settle("q1")
        until { state.proposalDialog == null }

        assertThat(actions.perform(AppShortcut.TOGGLE_SIDEBAR)).isTrue()
    }
}
