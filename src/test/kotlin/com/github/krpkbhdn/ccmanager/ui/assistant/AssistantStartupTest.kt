package com.github.krpkbhdn.ccmanager.ui.assistant

import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AssistantStartupTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mode = AssistantStartup.ASK
    @Volatile private var blocked = false
    private val background = AtomicInteger()
    private val foreground = AtomicInteger()
    private val state = AssistantStartupState(scope, { mode }, { mode = it }, { blocked }, { background.incrementAndGet() }, { foreground.incrementAndGet() }, pollMillis = 10)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `step depends on mode, claude and whether it was already handled`() {
        assertThat(startupStep(AssistantStartup.ASK, true, false)).isEqualTo(StartupStep.ASK)
        assertThat(startupStep(AssistantStartup.AUTO_START, true, false)).isEqualTo(StartupStep.START)
        assertThat(startupStep(AssistantStartup.NEVER, true, false)).isEqualTo(StartupStep.NOTHING)
        assertThat(startupStep(AssistantStartup.ASK, false, false)).isEqualTo(StartupStep.NOTHING)
        AssistantStartup.entries.forEach { assertThat(startupStep(it, true, true)).isEqualTo(StartupStep.NOTHING) }
    }

    @Test
    fun `auto start launches in the background without asking`() {
        mode = AssistantStartup.AUTO_START
        state.atStart(true)
        assertThat(background.get()).isEqualTo(1)
        assertThat(state.asking).isFalse()
    }

    @Test
    fun `asking waits for other dialogs and happens once per run`() {
        blocked = true
        state.atStart(true)
        Thread.sleep(60)
        assertThat(state.asking).isFalse()
        blocked = false
        await { state.asking }
        state.dismiss()
        state.atStart(true)
        Thread.sleep(60)
        assertThat(state.asking).isFalse()
    }

    @Test
    fun `start with dont ask again saves auto start and starts`() {
        state.atStart(true)
        await { state.asking }
        state.dontAskAgain = true
        state.choose(true)
        assertThat(mode).isEqualTo(AssistantStartup.AUTO_START)
        assertThat(foreground.get()).isEqualTo(1)
    }

    @Test
    fun `not now with dont ask again saves never and plain not now saves nothing`() {
        state.dontAskAgain = true
        state.choose(false)
        assertThat(mode).isEqualTo(AssistantStartup.NEVER)
        mode = AssistantStartup.ASK
        state.choose(false)
        assertThat(mode).isEqualTo(AssistantStartup.ASK)
        assertThat(foreground.get()).isZero()
    }
}
