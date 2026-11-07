package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.CopyOnWriteArrayList

private const val PAST_HOLD_THRESHOLD_MILLIS = 400L

/** Virtual time for [VoiceDictation]: the UI scope and the start dispatcher are stepped by hand, nothing waits on the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DictationLoop {
    private val uiScheduler = TestCoroutineScheduler()
    private val ioScheduler = TestCoroutineScheduler()
    val io = StandardTestDispatcher(ioScheduler)
    val scope = CoroutineScope(StandardTestDispatcher(uiScheduler))

    /** Moves the UI clock on; session starts queued on the start dispatcher keep waiting until [pump]. */
    fun advance(millis: Long) {
        uiScheduler.runCurrent()
        uiScheduler.advanceTimeBy(millis)
        uiScheduler.runCurrent()
    }

    /** Runs everything that is due, UI and start dispatcher alternately until the hand-offs between them settle. */
    fun pump() = repeat(4) {
        uiScheduler.runCurrent()
        ioScheduler.runCurrent()
    }
}

@OptIn(InternalComposeUiApi::class)
class VoiceDictationTest {
    private val loop = DictationLoop()
    private val scope = loop.scope
    private val listeners = CopyOnWriteArrayList<DictationListener>()
    private val session = mock(DictationSession::class.java)
    private val errors = CopyOnWriteArrayList<String>()
    private var usable = true
    private val state = TextFieldState()

    private val dictation by lazy {
        VoiceDictation(state, scope, { usable }, { listener -> listeners += listener; session }, { errors += it }, io = loop.io)
    }

    private fun failingDictation(error: Throwable) = VoiceDictation(state, scope, { true }, { throw error }, { errors += it }, io = loop.io)

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun key(key: Key, type: KeyEventType, ctrl: Boolean = false) = KeyEvent(key, type, isCtrlPressed = ctrl)

    private fun spaceDown(ctrl: Boolean = false) = key(Key.Spacebar, KeyEventType.KeyDown, ctrl)
    private fun spaceUp() = key(Key.Spacebar, KeyEventType.KeyUp)
    private fun letter() = key(Key.A, KeyEventType.KeyDown)

    private fun typeSpace() { state.edit { append(' ') } }

    private fun holdPastThreshold() {
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)
    }

    private fun holdUntilActive() {
        holdPastThreshold()
        loop.pump()
        assertThat(dictation.active).isTrue()
    }

    @Test
    fun `a tap passes the space through and never starts`() {
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        assertThat(dictation.onKeyEvent(spaceUp())).isFalse()
        loop.advance(600)

        assertThat(dictation.active).isFalse()
        assertThat(listeners).isEmpty()
        assertThat(state.text.toString()).isEqualTo(" ")
    }

    @Test
    fun `a hold removes the typed spaces and starts`() {
        state.edit { append("ab"); selection = TextRange(2) }
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)
        loop.pump()

        assertThat(dictation.active).isTrue()
        assertThat(state.text.toString()).isEqualTo("ab")
        assertThat(listeners).isNotEmpty()
    }

    @Test
    fun `a selection replaced by the first space leaves no stray space before the speech`() {
        state.edit { append("foo"); selection = TextRange(0, 3) }
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        state.edit { replace(0, 3, " "); selection = TextRange(1) }
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)
        loop.pump()

        listeners[0].onFinal("hello")
        loop.pump()

        assertThat(dictation.active).isTrue()
        assertThat(state.text.toString()).isEqualTo("hello")
    }

    @Test
    fun `text changed behind the dictation ends it without writing at a stale offset`() {
        holdUntilActive()
        listeners[0].onPartial("one")
        loop.pump()
        assertThat(state.text.toString()).isEqualTo("one")
        state.edit { replace(0, 0, "XYZ") }

        listeners[0].onFinal("two")
        loop.pump()

        assertThat(dictation.active).isFalse()
        assertThat(state.text.toString()).isEqualTo("XYZone")
    }

    @Test
    fun `partial then final are written and the highlight clears on release`() {
        holdUntilActive()
        listeners[0].onPartial("hel")
        loop.pump()
        assertThat(state.text.toString()).isEqualTo("hel")
        listeners[0].onFinal("hello")
        loop.pump()
        assertThat(state.text.toString()).isEqualTo("hello")

        assertThat(dictation.onKeyEvent(spaceUp())).isTrue()
        loop.pump()

        assertThat(dictation.active).isFalse()
        verify(session).close()
        assertThat(state.text.toString()).isEqualTo("hello")
    }

    @Test
    fun `release before the session has started still closes it`() {
        holdPastThreshold()
        assertThat(dictation.active).isTrue()
        dictation.onKeyEvent(spaceUp())
        loop.pump()

        assertThat(dictation.active).isFalse()
        verify(session).close()
    }

    @Test
    fun `an error reports once and ends the run`() {
        holdUntilActive()
        listeners[0].onPartial("x")
        loop.pump()
        assertThat(state.text.toString()).isEqualTo("x")
        listeners[0].onError(RuntimeException("mic gone"))
        loop.pump()

        assertThat(dictation.active).isFalse()
        assertThat(errors).containsExactly("mic gone")
        listeners[0].onPartial("late")
        loop.pump()
        assertThat(state.text.toString()).isEqualTo("x")
    }

    @Test
    fun `a failing start reports and returns to inactive`() {
        val failing = failingDictation(IllegalStateException("no mic"))
        failing.onKeyEvent(spaceDown())
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)
        loop.pump()
        failing.onKeyEvent(spaceUp())
        loop.pump()

        assertThat(failing.active).isFalse()
        assertThat(errors).containsExactly("no mic")
        assertThat(failing.status).isEqualTo(DictationStatus.Failed("no mic"))
    }

    @Test
    fun `keys are blocked while listening and Escape stops it`() {
        holdUntilActive()
        assertThat(dictation.listening).isTrue()

        assertThat(dictation.onKeyEvent(letter())).isTrue()
        assertThat(dictation.onKeyEvent(spaceDown())).isTrue()
        assertThat(dictation.onKeyEvent(key(Key.Escape, KeyEventType.KeyDown))).isTrue()
        loop.pump()
        assertThat(dictation.active).isFalse()
        verify(session).close()
        assertThat(dictation.onKeyEvent(spaceDown())).isTrue()
        dictation.onKeyEvent(spaceUp())
        assertThat(dictation.onKeyEvent(letter())).isFalse()
    }

    @Test
    fun `keys are used up while the session loads and free after release`() {
        holdPastThreshold()

        assertThat(dictation.status).isEqualTo(DictationStatus.Initializing)
        assertThat(dictation.onKeyEvent(letter())).isTrue()
        assertThat(dictation.onKeyEvent(key(Key.Unknown, KeyEventType.Unknown))).isTrue()
        dictation.onKeyEvent(spaceUp())
        assertThat(dictation.onKeyEvent(letter())).isFalse()
    }

    @Test
    fun `status goes from initializing to listening and level is passed on`() {
        holdPastThreshold()
        assertThat(dictation.status).isEqualTo(DictationStatus.Initializing)
        loop.pump()
        assertThat(dictation.status).isEqualTo(DictationStatus.Listening)
        listeners[0].onLevel(0.5)
        loop.pump()
        assertThat(dictation.level).isEqualTo(0.5f)

        dictation.onKeyEvent(spaceUp())
        loop.pump()

        assertThat(dictation.status).isEqualTo(DictationStatus.Idle)
        assertThat(dictation.level).isEqualTo(0f)
    }

    @Test
    fun `losing focus mid hold cancels the pending start and forgets the held space`() {
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        dictation.lostFocus()
        loop.advance(600)

        assertThat(dictation.active).isFalse()
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
    }

    @Test
    fun `a native failure on start is reported and does not leave the field active`() {
        val failing = failingDictation(UnsatisfiedLinkError("whisper"))
        failing.onKeyEvent(spaceDown())
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)
        loop.pump()

        assertThat(errors).containsExactly("whisper")
        assertThat(failing.active).isFalse()
    }

    @Test
    fun `spaces the user typed before the hold stay`() {
        state.edit { append("a  "); selection = TextRange(3) }
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        loop.advance(PAST_HOLD_THRESHOLD_MILLIS)

        assertThat(dictation.active).isTrue()
        assertThat(state.text.toString()).isEqualTo("a  ")
    }

    @Test
    fun `another key before the threshold cancels the hold`() {
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        assertThat(dictation.onKeyEvent(letter())).isFalse()
        loop.advance(600)

        assertThat(dictation.active).isFalse()
        assertThat(state.text.toString()).isEqualTo(" ")
    }

    @Test
    fun `unusable voice and modified space behave normally`() {
        usable = false
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        dictation.onKeyEvent(spaceUp())
        usable = true
        assertThat(dictation.onKeyEvent(spaceDown(ctrl = true))).isFalse()
        loop.advance(600)

        assertThat(dictation.active).isFalse()
        assertThat(listeners).isEmpty()
    }
}
