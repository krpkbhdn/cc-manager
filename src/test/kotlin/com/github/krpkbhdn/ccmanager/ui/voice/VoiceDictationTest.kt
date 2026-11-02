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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real time with a single-thread-free scope: the 350 ms hold threshold is real, so each test waits a little. */
@OptIn(InternalComposeUiApi::class)
class VoiceDictationTest {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val listeners = CopyOnWriteArrayList<DictationListener>()
    private val session = mock(DictationSession::class.java)
    private val errors = CopyOnWriteArrayList<String>()
    private val startGate = CountDownLatch(1)
    private var usable = true
    private var blockStart = false
    private val state = TextFieldState()

    private val dictation by lazy {
        VoiceDictation(state, scope, { usable }, { listener ->
            listeners += listener
            if (blockStart) startGate.await(5, TimeUnit.SECONDS)
            session
        }, { errors += it })
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun key(key: Key, type: KeyEventType, ctrl: Boolean = false) = KeyEvent(key, type, isCtrlPressed = ctrl)

    private fun spaceDown(ctrl: Boolean = false) = key(Key.Spacebar, KeyEventType.KeyDown, ctrl)
    private fun spaceUp() = key(Key.Spacebar, KeyEventType.KeyUp)
    private fun letter() = key(Key.A, KeyEventType.KeyDown)

    private fun typeSpace() { state.edit { append(' ') } }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < end) { "timeout waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun holdUntilActive() {
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        waitFor("active") { dictation.active }
    }

    @Test
    fun `a tap passes the space through and never starts`() {
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        typeSpace()
        assertThat(dictation.onKeyEvent(spaceUp())).isFalse()
        Thread.sleep(600)

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
        waitFor("active") { dictation.active }

        assertThat(state.text.toString()).isEqualTo("ab")
        waitFor("session start") { listeners.isNotEmpty() }
    }

    @Test
    fun `a selection replaced by the first space leaves no stray space before the speech`() {
        state.edit { append("foo"); selection = TextRange(0, 3) }
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
        state.edit { replace(0, 3, " "); selection = TextRange(1) }
        waitFor("active") { dictation.active }
        waitFor("listener") { listeners.isNotEmpty() }

        listeners[0].onFinal("hello")

        waitFor("final") { state.text.toString().isNotEmpty() }
        assertThat(state.text.toString()).isEqualTo("hello")
    }

    @Test
    fun `text changed behind the dictation ends it without writing at a stale offset`() {
        holdUntilActive()
        waitFor("listener") { listeners.isNotEmpty() }
        listeners[0].onPartial("one")
        waitFor("partial") { state.text.toString() == "one" }
        state.edit { replace(0, 0, "XYZ") }

        listeners[0].onFinal("two")

        waitFor("inactive") { !dictation.active }
        assertThat(state.text.toString()).isEqualTo("XYZone")
    }

    @Test
    fun `partial then final are written and the highlight clears on release`() {
        holdUntilActive()
        waitFor("listener") { listeners.isNotEmpty() }
        listeners[0].onPartial("hel")
        waitFor("partial") { state.text.toString() == "hel" }
        listeners[0].onFinal("hello")
        waitFor("final") { state.text.toString() == "hello" }

        assertThat(dictation.onKeyEvent(spaceUp())).isTrue()
        waitFor("inactive") { !dictation.active }
        verify(session).close()
        assertThat(state.text.toString()).isEqualTo("hello")
    }

    @Test
    fun `release before the session has started still closes it`() {
        blockStart = true
        holdUntilActive()
        dictation.onKeyEvent(spaceUp())
        startGate.countDown()

        waitFor("inactive") { !dictation.active }
        verify(session).close()
    }

    @Test
    fun `an error reports once and ends the run`() {
        holdUntilActive()
        waitFor("listener") { listeners.isNotEmpty() }
        listeners[0].onPartial("x")
        waitFor("partial") { state.text.toString() == "x" }
        listeners[0].onError(RuntimeException("mic gone"))

        waitFor("error") { errors.isNotEmpty() }
        waitFor("inactive") { !dictation.active }
        assertThat(errors).containsExactly("mic gone")
        listeners[0].onPartial("late")
        Thread.sleep(100)
        assertThat(state.text.toString()).isEqualTo("x")
    }

    @Test
    fun `a failing start reports and returns to inactive`() {
        val failing = VoiceDictation(state, scope, { true }, { throw IllegalStateException("no mic") }, { errors += it })
        failing.onKeyEvent(spaceDown())
        waitFor("active or failed") { errors.isNotEmpty() }
        failing.onKeyEvent(spaceUp())
        waitFor("inactive") { !failing.active }

        assertThat(errors).containsExactly("no mic")
        assertThat(failing.status).isEqualTo(DictationStatus.Failed("no mic"))
    }

    @Test
    fun `keys are blocked while listening and Escape stops it`() {
        holdUntilActive()
        waitFor("listening") { dictation.listening }

        assertThat(dictation.onKeyEvent(letter())).isTrue()
        assertThat(dictation.onKeyEvent(spaceDown())).isTrue()
        assertThat(dictation.onKeyEvent(key(Key.Escape, KeyEventType.KeyDown))).isTrue()
        waitFor("inactive") { !dictation.active }
        verify(session).close()
        assertThat(dictation.onKeyEvent(spaceDown())).isTrue()
        dictation.onKeyEvent(spaceUp())
        assertThat(dictation.onKeyEvent(letter())).isFalse()
    }

    @Test
    fun `keys are used up while the session loads and free after release`() {
        blockStart = true
        holdUntilActive()

        assertThat(dictation.status).isEqualTo(DictationStatus.Initializing)
        assertThat(dictation.onKeyEvent(letter())).isTrue()
        assertThat(dictation.onKeyEvent(key(Key.Unknown, KeyEventType.Unknown))).isTrue()
        dictation.onKeyEvent(spaceUp())
        assertThat(dictation.onKeyEvent(letter())).isFalse()
        startGate.countDown()
    }

    @Test
    fun `status goes from initializing to listening and level is passed on`() {
        holdUntilActive()
        waitFor("listening") { dictation.status == DictationStatus.Listening }
        waitFor("listener") { listeners.isNotEmpty() }
        listeners[0].onLevel(0.5)
        waitFor("level") { dictation.level == 0.5f }
        dictation.onKeyEvent(spaceUp())
        waitFor("idle") { dictation.status == DictationStatus.Idle && dictation.level == 0f }
    }

    @Test
    fun `losing focus mid hold cancels the pending start and forgets the held space`() {
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        dictation.lostFocus()
        Thread.sleep(600)

        assertThat(dictation.active).isFalse()
        assertThat(dictation.onKeyEvent(spaceDown())).isFalse()
    }

    @Test
    fun `a native failure on start is reported and does not leave the field active`() {
        val failing = VoiceDictation(state, scope, { true }, { throw UnsatisfiedLinkError("whisper") }, { errors += it })
        failing.onKeyEvent(spaceDown())
        waitFor("error") { errors.isNotEmpty() }
        waitFor("inactive") { !failing.active }
    }

    @Test
    fun `spaces the user typed before the hold stay`() {
        state.edit { append("a  "); selection = TextRange(3) }
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        waitFor("active") { dictation.active }

        assertThat(state.text.toString()).isEqualTo("a  ")
    }

    @Test
    fun `another key before the threshold cancels the hold`() {
        dictation.onKeyEvent(spaceDown())
        typeSpace()
        assertThat(dictation.onKeyEvent(letter())).isFalse()
        Thread.sleep(600)

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
        Thread.sleep(600)

        assertThat(dictation.active).isFalse()
        assertThat(listeners).isEmpty()
    }
}
