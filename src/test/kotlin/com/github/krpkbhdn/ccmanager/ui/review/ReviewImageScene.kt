package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.ReviewPreviewData
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat

private const val FRAME_NANOS = 16_000_000L
private const val SEEDED = 1L

private class Pump : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
    fun run() { while (true) queue.poll()?.run() ?: return }
}

/** ReviewImageView over the preview review data in an offscreen scene, driven by synthetic pointer events. */
@OptIn(InternalComposeUiApi::class)
internal class ReviewImageScene(width: Int = 1000, height: Int = 700) : AutoCloseable {
    private val pump = Pump()
    private val scope = CoroutineScope(SupervisorJob() + pump)
    val state: ReviewState
    val viewer: ReviewViewerState
    val imageId: Long
    private val scene: ImageComposeScene
    private var nanos = 0L
    private var clock = 0L

    init {
        val service = ReviewPreviewData.service()
        ReviewPreviewData.seed(SEEDED)
        state = ReviewState(service, { LiveSessions() }, { _, _ -> }, { _, _, _, _ -> }, scope, { })
        state.start()
        await { state.reviews(SEEDED).isNotEmpty() }
        val review = state.selectedReview(SEEDED)!!
        await { state.images(review.id).isNotEmpty() }
        val image = state.images(review.id).first()
        await { state.bitmap(image) != null && state.marks(image.id).size == 4 }
        imageId = image.id
        viewer = ReviewViewerState(image.width.toFloat(), image.height.toFloat())
        scene = ImageComposeScene(width, height, Density(1f)) {
            AppTheme(true) { ReviewImageView(viewer, state.bitmap(image), image.id, state, Modifier) }
        }
        frames(10)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        pump.run()
        while (!condition() && System.currentTimeMillis() < deadline) { Thread.sleep(20); pump.run() }
    }

    fun frames(count: Int = 5) = repeat(count) { pump.run(); scene.render(nanos); nanos += FRAME_NANOS }

    fun png(): ByteArray = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes

    fun screen(nx: Double, ny: Double): Offset {
        val (x, y) = viewer.viewport!!.toScreen((nx * viewer.imageW).toFloat(), (ny * viewer.imageH).toFloat())
        return Offset(x, y)
    }

    fun pointer(type: PointerEventType, at: Offset, button: PointerButton? = null, modifiers: PointerKeyboardModifiers = PointerKeyboardModifiers()) {
        clock += 30
        val down = type != PointerEventType.Release && button != null
        val buttons = PointerButtons(isPrimaryPressed = down && button == PointerButton.Primary, isSecondaryPressed = down && button == PointerButton.Secondary, isTertiaryPressed = down && button == PointerButton.Tertiary)
        scene.sendPointerEvent(type, at, Offset.Zero, clock, buttons = buttons, keyboardModifiers = modifiers, button = button)
        frames(2)
    }

    fun click(at: Offset, button: PointerButton = PointerButton.Primary) {
        pointer(PointerEventType.Move, at)
        pointer(PointerEventType.Press, at, button)
        pointer(PointerEventType.Release, at, button)
    }

    fun drag(from: Offset, to: Offset, button: PointerButton = PointerButton.Primary, modifiers: PointerKeyboardModifiers = PointerKeyboardModifiers()) {
        pointer(PointerEventType.Move, from, modifiers = modifiers)
        pointer(PointerEventType.Press, from, button, modifiers)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { pointer(PointerEventType.Move, from + (to - from) * it, button, modifiers) }
        pointer(PointerEventType.Release, to, button, modifiers)
    }

    fun key(key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        frames(2)
    }

    override fun close() {
        scope.cancel()
        runCatching { scene.close() }
    }
}
