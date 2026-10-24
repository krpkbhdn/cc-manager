package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

private const val FRAME_NANOS = 16_000_000L
private const val FIT_FRAMES = 15
internal val HEADER_COLOR = Color(0xFFFF00FF)

/** A terminal view rendered into an offscreen Compose scene, no OS window involved. */
internal class TerminalScene(
    val session: TerminalSession,
    private val connector: ScriptedConnector,
    width: Int,
    height: Int,
    theme: TerminalTheme = TerminalTheme(),
    density: Float = 1f,
    headerPixels: Int = 0,
) : AutoCloseable {
    private val scene = ImageComposeScene(width, height, Density(density)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { headerPixels.toDp() }).background(HEADER_COLOR))
            TerminalSessionView(session, Modifier.fillMaxWidth().weight(1f), theme)
        }
    }
    private var nanos = 0L

    val cellHeight: Int = TerminalFonts(8).cellHeight

    init {
        session.theme = theme
        awaitFit()
    }

    private fun awaitFit() {
        repeat(FIT_FRAMES) {
            frame()
            Thread.sleep(40)
        }
        settle(connector, session)
    }

    fun sendKey(event: KeyEvent): Boolean = scene.sendKeyEvent(event)

    fun sendPointer(type: PointerEventType, at: Offset, timeMillis: Long, button: PointerButton?, scroll: Offset) {
        val buttons = PointerButtons(isPrimaryPressed = button == PointerButton.Primary && type != PointerEventType.Release)
        scene.sendPointerEvent(type, at, scroll, timeMillis, buttons = buttons, button = button)
    }

    fun frame(): Image {
        nanos += FRAME_NANOS
        return scene.render(nanos)
    }

    fun play(text: String): Image {
        connector.feed(text)
        settle(connector, session)
        repeat(3) { frame() }
        return frame()
    }

    fun image(): Image {
        repeat(3) { frame() }
        return frame()
    }

    override fun close() = scene.close()
}

internal fun Image.save(file: File) {
    file.parentFile.mkdirs()
    file.writeBytes(checkNotNull(encodeToData(EncodedImageFormat.PNG)).bytes)
}

internal fun Image.toBitmap(): Bitmap = Bitmap.makeFromImage(this)

internal fun Bitmap.rgbAt(x: Int, y: Int): Int = getColor(x, y) and 0xFFFFFF

internal fun Bitmap.count(rgb: Int, region: IntRange = 0 until width, rows: IntRange = 0 until height): Int =
    rows.sumOf { y -> region.count { x -> rgbAt(x, y) == rgb } }

internal fun shotDirectory(): File = File(System.getenv("CCM_SHOT_DIR") ?: "build/terminal-shots")
