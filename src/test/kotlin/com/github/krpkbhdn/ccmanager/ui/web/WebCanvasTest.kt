package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import java.awt.event.MouseEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val WIDTH = 200
private const val HEIGHT = 100
private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 12

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class WebCanvasTest {
    private val host = FakePageHost()
    private var scene: ImageComposeScene? = null
    private var nanos = 0L

    @AfterEach
    fun close() {
        scene?.close()
    }

    private fun show(density: Float, interaction: Boolean = true): ImageComposeScene =
        ImageComposeScene(WIDTH, HEIGHT, Density(density)) { WebCanvas(host, PageViewport((WIDTH / density).toInt(), (HEIGHT / density).toInt()), Modifier, interaction = interaction) }.also {
            scene = it
            frames(it, SETTLE_FRAMES)
        }

    private fun frames(scene: ImageComposeScene, count: Int) = repeat(count) {
        nanos += FRAME_NANOS
        scene.render(nanos)
    }

    private fun pixel(scene: ImageComposeScene, x: Int, y: Int): Int {
        nanos += FRAME_NANOS
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(scene.render(nanos))
        return bitmap.getColor(x, y)
    }

    @Test
    fun `frame and popup are drawn at physical pixels`() {
        val scene = show(density = 1f)
        host.emitFrame(WIDTH, HEIGHT, Color.Red)
        host.emitPopup(xDip = 20, yDip = 10, widthDip = 30, heightDip = 30, density = 1f, color = Color.Blue)
        frames(scene, SETTLE_FRAMES)

        assertThat(pixel(scene, 150, 80)).isEqualTo(0xFFFF0000.toInt())
        assertThat(pixel(scene, 30, 20)).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun `size and density are reported to the host in DIPs once`() {
        show(density = 2f)
        await { frames(scene!!, 1); host.resizes.isNotEmpty() }
        frames(scene!!, SETTLE_FRAMES)

        assertThat(host.resizes).hasSize(1)
        assertThat(host.resizes.single().widthDip).isEqualTo(WIDTH / 2)
        assertThat(host.resizes.single().heightDip).isEqualTo(HEIGHT / 2)
        assertThat(host.resizes.single().density).isEqualTo(2f)
    }

    @Test
    fun `pointer positions arrive as DIPs`() {
        val scene = show(density = 2f)
        val at = Offset(100f, 60f)
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(
            PointerEventType.Press, at, timeMillis = 10, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary,
        )
        scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = 20, button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Scroll, at, Offset(0f, 1f), timeMillis = 30)

        assertThat(host.mouseCalls.map { it.awtId }).contains(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)
        assertThat(host.mouseCalls).allSatisfy { assertThat(it.xDip).isEqualTo(50); assertThat(it.yDip).isEqualTo(30) }
        assertThat(host.scrollCalls.single().pixels).isEqualTo(100)
        assertThat(host.scrollCalls.single().xDip).isEqualTo(50)
    }

    @Test
    fun `a locked canvas sends the page no mouse and drives the inspector instead`() {
        val scene = show(density = 1f, interaction = false)
        val at = Offset(100f, 60f)
        host.mouseCalls.clear()

        scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 50f))
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(PointerEventType.Move, at)
        frames(scene, 3)
        scene.sendPointerEvent(PointerEventType.Move, at)
        frames(scene, 3)
        scene.sendPointerEvent(
            PointerEventType.Press, at, timeMillis = 10, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary,
        )
        scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = 20, button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Scroll, at, Offset(0f, 1f), timeMillis = 30)
        frames(scene, 3)

        assertThat(host.mouseCalls).isEmpty()
        assertThat(host.scrollCalls).isEmpty()
        assertThat(host.scripts.filter { it.contains("pointer(\"move\",100.0,60.0)") }).describedAs(host.scripts.toString()).hasSize(1)
        assertThat(host.scripts).anyMatch { it.contains("pointer(\"click\",100.0,60.0)") }
        assertThat(host.scripts).anyMatch { it.contains("scrollBy(0.0,100.0,100.0,60.0)") }
    }

    @Test
    fun `canvas does not close the host`() {
        show(density = 1f)
        scene!!.close()
        scene = null

        assertThat(host.closed).isFalse()
    }
}
