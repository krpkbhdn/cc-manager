package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.state.IdeMenuActions
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val FRAME_NANOS = 16_000_000L
private const val NOTEPAD_ROW_Y = 115f
private const val NAME_X = 100f
private const val STAR_X = 260f

class IdeMenuClickTest {
    private val opened = mutableListOf<String>()
    private val madeDefault = mutableListOf<String>()

    private fun click(x: Float, y: Float) {
        val actions = IdeMenuActions(opened::add, madeDefault::add, {}, {})
        val scene = ImageComposeScene(420, 330, Density(1f)) { AppTheme(true) { IdeMenuScene(sampleIdes, sampleIdes[0], emptyMap(), actions) } }
        try {
            var nanos = 0L
            repeat(3) { scene.render(nanos.also { nanos += FRAME_NANOS }) }
            val at = Offset(x, y)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
            scene.render(nanos.also { nanos += FRAME_NANOS })
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
            repeat(3) { scene.render(nanos.also { nanos += FRAME_NANOS }) }
        } finally {
            scene.close()
        }
    }

    @Test
    fun `clicking a row opens that IDE`() {
        click(NAME_X, NOTEPAD_ROW_Y)

        assertThat(opened).containsExactly("b")
        assertThat(madeDefault).isEmpty()
    }

    @Test
    fun `clicking the star makes the IDE default without opening it`() {
        click(STAR_X, NOTEPAD_ROW_Y)

        assertThat(madeDefault).containsExactly("b")
        assertThat(opened).isEmpty()
    }
}
