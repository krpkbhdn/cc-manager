package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScaledDensityTest {

    private class Measured(var px: Int = 0, var spPx: Float = 0f)

    private fun measure(scale: Scale): Measured {
        val out = Measured()
        val scene = ImageComposeScene(400, 400, Density(2f, 1f)) {
            CompositionLocalProvider(LocalScale provides scale) {
                ScaledDensity {
                    out.spPx = with(LocalDensity.current) { 10.sp.toPx() }
                    Box(Modifier.size(100.dp).onSizeChanged { out.px = it.width })
                }
            }
        }
        scene.render()
        scene.close()
        return out
    }

    @Test
    fun `default scale leaves density unchanged`() {
        val m = measure(Scale())
        assertThat(m.px).isEqualTo(200)
        assertThat(m.spPx).isEqualTo(20f)
    }

    @Test
    fun `interface scale multiplies dp and sp, font scale only sp`() {
        val m = measure(Scale(ui = 2f, font = 1.5f))
        assertThat(m.px).isEqualTo(400)
        assertThat(m.spPx).isEqualTo(60f)
    }
}
