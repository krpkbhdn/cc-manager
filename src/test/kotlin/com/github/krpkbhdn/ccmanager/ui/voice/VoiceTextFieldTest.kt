package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.components.DictationChrome
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CopyOnWriteArrayList

private const val FRAME_NANOS = 16_000_000L
private const val SPACE = 32

/** Hold-Space dictation through the real single-line [DictationField]. */
@OptIn(InternalComposeUiApi::class)
class VoiceTextFieldTest {
    private val listeners = CopyOnWriteArrayList<DictationListener>()
    private val state = TextFieldState()
    private var nanos = 0L

    private val scene = ImageComposeScene(600, 200, Density(1f)) {
        AppTheme(dark = true) {
            val scope = rememberCoroutineScope()
            val focus = remember { FocusRequester() }
            val dictation = remember { VoiceDictation(state, scope, { true }, { listeners += it; mock(DictationSession::class.java) }, {}, singleLine = true) }
            LaunchedEffect(Unit) { focus.requestFocus() }
            DictationField(state, dictation, usable = true, focusRequester = focus, placeholder = "Say it")
        }
    }

    private fun frame() { scene.render(nanos); nanos += FRAME_NANOS }

    private fun press() = scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyDown, SPACE))

    private fun typed() = scene.sendKeyEvent(KeyEvent(Key.Unknown, KeyEventType.Unknown, SPACE))

    private fun release() = scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyUp, SPACE))

    private fun holdUntilListening() {
        repeat(6) { frame() }
        press()
        typed()
        val end = System.nanoTime() + 700_000_000L
        while (System.nanoTime() < end && listeners.isEmpty()) {
            Thread.sleep(40)
            press()
            typed()
            frame()
        }
        Thread.sleep(300)
        repeat(6) { frame() }
    }

    @Test
    fun `a tap on Space passes through to the field and starts nothing`() {
        state.edit { append("ab"); selection = TextRange(2) }
        repeat(6) { frame() }
        val down = press()
        typed()
        val up = release()
        frame()

        assertThat(down || up).isFalse()
        assertThat(listeners).isEmpty()
        scene.close()
    }

    @Test
    fun `holding Space dictates at the caret and flattens line breaks`() {
        state.edit { append("ab cd"); selection = TextRange(2) }
        holdUntilListening()
        listeners[0].onFinal("hello\nworld")
        repeat(6) { frame(); Thread.sleep(20) }

        assertThat(state.text.toString()).isEqualTo("ab hello world cd")
        release()
        scene.close()
    }

    @Test
    fun `the placeholder is hidden only while dictating into an empty field`() {
        val chrome = { dictating: Boolean -> DictationChrome(null, dictating, dictating, null, 0f, { null }) { } }

        assertThat(chrome(false).showPlaceholder(TextFieldState())).isTrue()
        assertThat(chrome(true).showPlaceholder(TextFieldState())).isFalse()
        assertThat(chrome(false).showPlaceholder(TextFieldState("x"))).isFalse()
        scene.close()
    }
}
