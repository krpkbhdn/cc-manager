package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.never

private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 6
private const val VERIFY_MILLIS = 2_000L
private const val QUIET_MILLIS = 300L

/** One proposal dialog in an offscreen Compose scene: real key events go in, answers reach a mocked [ProposalService]. */
@OptIn(InternalComposeUiApi::class)
internal class ProposalDialogHarness(private val proposal: Proposal, dialog: @Composable (ProposalContext, FrameTitle) -> Unit) : AutoCloseable {
    val service: ProposalService = mock(ProposalService::class.java)
    val state = ProposalState(service, CoroutineScope(SupervisorJob() + Dispatchers.Default), {})
    private val title = FrameTitle(com.github.krpkbhdn.ccmanager.ui.icons.Icons.Message, "Test", androidx.compose.ui.graphics.Color.Gray, emptyList(), null)
    private val scene = ImageComposeScene(1000, 800, Density(1f)) { AppTheme(dark = true) { dialog(ProposalContext(state, proposal), title) } }
    private var nanos = 0L

    init {
        settle()
    }

    fun settle() = repeat(SETTLE_FRAMES) { scene.render(nanos); nanos += FRAME_NANOS }

    fun press(key: Key, ctrl: Boolean = false, shift: Boolean = false) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl, isShiftPressed = shift))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isCtrlPressed = ctrl, isShiftPressed = shift))
        settle()
    }


    fun answered(): ProposalAnswer {
        val captor = ArgumentCaptor.forClass(ProposalAnswer::class.java)
        verify(service, timeout(VERIFY_MILLIS)).answer(anyString(), captor.capture())
        return captor.value
    }

    fun assertNoAnswer() {
        Thread.sleep(QUIET_MILLIS)
        verify(service, never()).answer(anyString(), org.mockito.ArgumentMatchers.any(ProposalAnswer::class.java))
    }

    override fun close() {
        runCatching { scene.close() }
    }
}
