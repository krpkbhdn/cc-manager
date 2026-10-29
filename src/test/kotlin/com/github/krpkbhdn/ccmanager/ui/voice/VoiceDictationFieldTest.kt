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

/** Sends the key events a held Space produces on desktop (down, typed char, repeated) through the real [AppTextArea]. */
@OptIn(InternalComposeUiApi::class)
class VoiceDictationFieldTest {
    private val listeners = CopyOnWriteArrayList<DictationListener>()
    private val state = TextFieldState()
    private var nanos = 0L
    private val slowStart = java.util.concurrent.atomic.AtomicBoolean()

    private val scene = ImageComposeScene(600, 300, Density(1f)) {
        AppTheme(dark = true) {
            val scope = rememberCoroutineScope()
            val focus = remember { FocusRequester() }
            val dictation = remember { VoiceDictation(state, scope, { true }, { listeners += it; while (slowStart.get()) Thread.sleep(10); mock(DictationSession::class.java) }, {}) }
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
        val end = System.nanoTime() + 700_000_000L
        while (System.nanoTime() < end) {
            Thread.sleep(40)
            press()
            typed()
            frame()
        }

        assertThat(listeners).isNotEmpty()
        release()
        scene.close()
    }
    @Test
    fun `every key is used up while the session is still loading`() {
        slowStart.set(true)
        state.edit { append("ab"); selection = androidx.compose.ui.text.TextRange(2) }
        repeat(6) { frame() }
        press()
        typed()
        val end = System.nanoTime() + 900_000_000L
        while (System.nanoTime() < end) {
            Thread.sleep(40)
            press()
            typed()
            frame()
        }

        assertThat(state.text.toString()).isEqualTo("ab")
        assertThat(state.selection.min).isEqualTo(2)
        slowStart.set(false)
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
    fun `the caret bar sits in an empty field`() {
        holdUntilListening()
        listeners[0].onLevel(1.0)
        settle()
        shot("empty-loud")
        release()
        scene.close()
    }

    private fun settle(millis: Long = 400) {
        val end = System.nanoTime() + millis * 1_000_000
        while (System.nanoTime() < end) { frame(); Thread.sleep(16) }
    }

    private fun shot(name: String): List<Byte> {
        val data = org.jetbrains.skia.Image.makeFromBitmap(scene.render(nanos).also { nanos += FRAME_NANOS }.let { bitmap ->
            org.jetbrains.skia.Bitmap().also { it.allocN32Pixels(bitmap.width, bitmap.height); bitmap.readPixels(it) }
        }).encodeToData()!!.bytes
        System.getenv("CCM_SHOT_DIR")?.let { java.io.File(it).apply { mkdirs() }.resolve("dictation-caret-$name.png").writeBytes(data) }
        return data.toList()
    }
}
