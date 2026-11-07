package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CopyOnWriteArrayList

private const val FRAME_NANOS = 16_000_000L
private const val SPACE = 32
private const val KEY_REPEAT_MILLIS = 40L
private const val LOADING_KEY_REPEATS = 23

/** Sends the key events a held Space produces on desktop (down, typed char, repeated) through the real [AppTextArea]. */
@OptIn(InternalComposeUiApi::class)
class VoiceDictationFieldTest {
    private val listeners = CopyOnWriteArrayList<DictationListener>()
    private val state = TextFieldState()
    private var nanos = 0L
    private val loop = DictationLoop()

    private val scene = ImageComposeScene(600, 300, Density(1f)) {
        AppTheme(dark = true) {
            val focus = remember { FocusRequester() }
            val dictation = remember { VoiceDictation(state, loop.scope, { true }, { listeners += it; mock(DictationSession::class.java) }, {}, io = loop.io) }
            LaunchedEffect(Unit) { focus.requestFocus() }
            AppTextArea(state, focusRequester = focus, voice = dictation)
        }
    }

    private fun frame() { scene.render(nanos); nanos += FRAME_NANOS }

    private fun press() = scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyDown, SPACE))

    private fun typed() = scene.sendKeyEvent(KeyEvent(Key.Unknown, KeyEventType.Unknown, SPACE))

    private fun release() = scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyUp, SPACE))

    @Test
    fun `holding Space past the threshold starts dictation`() {
        repeat(6) { frame() }
        press()
        typed()
        repeat(18) { repeatKey() }
        loop.pump()

        assertThat(listeners).isNotEmpty()
        release()
        scene.close()
    }
    @Test
    fun `every key is used up while the session is still loading`() {
        state.edit { append("ab"); selection = androidx.compose.ui.text.TextRange(2) }
        repeat(6) { frame() }
        press()
        typed()
        repeat(LOADING_KEY_REPEATS) { repeatKey() }

        assertThat(state.text.toString()).isEqualTo("ab")
        assertThat(state.selection.min).isEqualTo(2)
        release()
        scene.close()
    }
    @Test
    fun `the caret bar follows the level while listening`() {
        state.edit { append("ab cd"); selection = androidx.compose.ui.text.TextRange(2) }
        holdUntilListening()
        val quiet = shot("quiet")
        listeners[0].onLevel(0.5)
        settle()
        val mid = shot("mid")
        listeners[0].onLevel(1.0)
        settle()
        val loud = shot("loud")
        settle(700)
        val loudLater = shot("loud-later")

        assertThat(mid).isNotEqualTo(quiet)
        assertThat(loud).isNotEqualTo(mid)
        assertThat(loudLater).isNotEqualTo(loud)
        release()
        scene.close()
    }

    private fun holdUntilListening() {
        repeat(6) { frame() }
        press()
        typed()
        var repeats = 0
        while (listeners.isEmpty()) {
            check(++repeats < 100) { "dictation never started" }
            repeatKey()
            loop.pump()
        }
        repeat(6) { frame() }
    }

    @Test
    fun `the caret bar sits in an empty field`() {
        holdUntilListening()
        listeners[0].onLevel(1.0)
        settle()
        shot("empty-loud")
        release()
        scene.close()
    }

    private fun repeatKey() {
        loop.advance(KEY_REPEAT_MILLIS)
        press()
        typed()
        frame()
    }

    private fun settle(millis: Long = 400) {
        loop.pump()
        repeat((millis * 1_000_000 / FRAME_NANOS).toInt()) { frame() }
    }

    private fun shot(name: String): List<Byte> {
        val data = org.jetbrains.skia.Image.makeFromBitmap(scene.render(nanos).also { nanos += FRAME_NANOS }.let { bitmap ->
            org.jetbrains.skia.Bitmap().also { it.allocN32Pixels(bitmap.width, bitmap.height); bitmap.readPixels(it) }
        }).encodeToData()!!.bytes
        System.getenv("CCM_SHOT_DIR")?.let { java.io.File(it).apply { mkdirs() }.resolve("dictation-caret-$name.png").writeBytes(data) }
        return data.toList()
    }
}
