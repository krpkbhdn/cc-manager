package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.skia.Bitmap
import org.junit.jupiter.api.Test

private const val FRAME_NANOS = 16_000_000L
private const val FRAMES = 30
private const val PILL_X = 22
private const val PILL_Y = 32
private const val CHANNEL_MASK = 0xFF

/** Hover on a tab inside a dialog card must fade in and stay: no dark dip while fading, and visibly different from the card. */
class TabPillHoverTest {
    private fun hoverPixels(selected: Boolean, repeatMoves: Boolean): List<Int> {
        val scene = ImageComposeScene(300, 100, Density(1f)) {
            AppTheme(dark = true) {
                Box(Modifier.background(colors.surfaceRaised)) {
                    Row(Modifier.padding(20.dp)) { PresetTab.entries.take(3).forEach { TabPill(it, 2, selected && it == PresetTab.INSTRUCTIONS) {} } }
                }
            }
        }
        try {
            var nanos = 0L
            fun render(): Bitmap = Bitmap.makeFromImage(scene.render(nanos.also { nanos += FRAME_NANOS }))
            repeat(3) { render() }
            val at = Offset(40f, PILL_Y.toFloat())
            scene.sendPointerEvent(PointerEventType.Enter, at)
            scene.sendPointerEvent(PointerEventType.Move, at)
            return (0 until FRAMES).map {
                if (repeatMoves) scene.sendPointerEvent(PointerEventType.Move, at)
                render().getColor(PILL_X, PILL_Y) and 0xFFFFFF
            }
        } finally {
            scene.close()
        }
    }

    private fun Int.green() = (this shr 8) and CHANNEL_MASK

    private fun assertFadesInAndStays(frames: List<Int>) {
        val start = frames.first().green()
        val end = frames.last().green()
        println("hover pixels: " + frames.joinToString { "%06X".format(it) })
        assertThat(frames.map { it.green() }).allMatch { it in minOf(start, end)..maxOf(start, end) }
        assertThat(frames.takeLast(FRAMES / 2).distinct()).hasSize(1)
    }

    @Test
    fun `unselected tab fades in without a dip and stays hovered`() {
        val frames = hoverPixels(selected = false, repeatMoves = true)
        assertFadesInAndStays(frames)
        assertThat(frames.last().green() - frames.first().green()).isGreaterThanOrEqualTo(MIN_VISIBLE_STEP)
    }

    @Test
    fun `hover stays without repeated move events`() {
        assertFadesInAndStays(hoverPixels(selected = false, repeatMoves = false))
    }

    @Test
    fun `selected tab keeps its accent background while hovered`() {
        val frames = hoverPixels(selected = true, repeatMoves = true)
        assertFadesInAndStays(frames)
    }

    private companion object {
        const val MIN_VISIBLE_STEP = 8
    }
}
