package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicInteger

private const val FRAME_NANOS = 16_000_000L
private const val SPACE = 32

/** Two dictation fields in one scene: holding Space dictates only into the one with focus. */
@OptIn(InternalComposeUiApi::class)
class VoiceTextAreaTest {
    private val firstStarts = AtomicInteger()
    private val secondStarts = AtomicInteger()
    private var nanos = 0L

    private val scene = ImageComposeScene(600, 400, Density(1f)) {
        AppTheme(dark = true) {
            val scope = rememberCoroutineScope()
            val first = remember { TextFieldState() }
            val second = remember { TextFieldState() }
            val focus = remember { FocusRequester() }
            val firstDictation = remember { VoiceDictation(first, scope, { true }, { firstStarts.incrementAndGet(); mock(DictationSession::class.java) }, {}) }
            val secondDictation = remember { VoiceDictation(second, scope, { true }, { secondStarts.incrementAndGet(); mock(DictationSession::class.java) }, {}) }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Column {
                DictationArea(first, firstDictation, usable = true)
                DictationArea(second, secondDictation, usable = true, focusRequester = focus)
            }
        }
    }

    private fun frame() { scene.render(nanos); nanos += FRAME_NANOS }

    private fun key(type: KeyEventType) = scene.sendKeyEvent(KeyEvent(Key.Spacebar, type, SPACE))

    @Test
    fun `holding Space dictates only into the focused field`() {
        repeat(6) { frame() }
        key(KeyEventType.KeyDown)
        val end = System.nanoTime() + 700_000_000L
        while (System.nanoTime() < end && secondStarts.get() == 0) {
            Thread.sleep(40)
            key(KeyEventType.KeyDown)
            frame()
        }

        assertThat(secondStarts.get()).isEqualTo(1)
        assertThat(firstStarts.get()).isZero()
        key(KeyEventType.KeyUp)
        scene.close()
    }
}
