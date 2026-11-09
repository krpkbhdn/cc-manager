package com.github.krpkbhdn.ccmanager.ui.review

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test

class ReviewViewportTest {
    private val near = Offset.offset(0.001f)

    @Test
    fun `fit centres the image and leaves a margin`() {
        val fit = Viewport.fit(1000f, 500f, 532f, 400f)
        assertThat(fit.scale).isEqualTo(0.5f, near)
        assertThat(fit.offsetX).isEqualTo(16f, near)
        assertThat(fit.offsetY).isEqualTo(75f, near)
    }

    @Test
    fun `zoom keeps the image point under the anchor fixed`() {
        val start = Viewport.fit(1000f, 500f, 800f, 600f)
        val before = start.toImage(300f, 200f)
        val after = start.zoomAt(300f, 200f, 2f).toImage(300f, 200f)
        assertThat(after.first).isEqualTo(before.first, near)
        assertThat(after.second).isEqualTo(before.second, near)
    }

    @Test
    fun `zoom is limited to the minimum and 16x`() {
        val v = Viewport(1f, 0f, 0f)
        assertThat(v.zoomAt(0f, 0f, 100f).scale).isEqualTo(16f)
        assertThat(v.zoomAt(0f, 0f, 0.001f, minScale = 0.2f).scale).isEqualTo(0.2f)
    }

    @Test
    fun `minimum scale is a quarter of the fit scale`() {
        val fit = Viewport.fit(1000f, 500f, 532f, 400f).scale
        assertThat(Viewport.minScale(1000f, 500f, 532f, 400f)).isEqualTo(fit / 4, near)
    }

    @Test
    fun `screen and image coordinates round trip`() {
        val v = Viewport(2.5f, 40f, -12f)
        val (ix, iy) = v.toImage(310f, 90f)
        val (sx, sy) = v.toScreen(ix, iy)
        assertThat(sx).isEqualTo(310f, near)
        assertThat(sy).isEqualTo(90f, near)
    }

    @Test
    fun `pan moves the offset only`() {
        assertThat(Viewport(2f, 1f, 2f).pan(3f, -4f)).isEqualTo(Viewport(2f, 4f, -2f))
    }

    @Test
    fun `actual size is scale one and centred`() {
        assertThat(Viewport.actualSize(400f, 200f, 800f, 600f)).isEqualTo(Viewport(1f, 200f, 200f))
    }

    @Test
    fun `clamping keeps part of the image visible`() {
        val far = Viewport(1f, 5000f, -5000f).clamped(1000f, 500f, 800f, 600f)
        assertThat(far.offsetX).isEqualTo(736f)
        assertThat(far.offsetY).isEqualTo(-436f)
    }

    @Test
    fun `clamping leaves a visible position alone`() {
        val v = Viewport(1f, 10f, 20f)
        assertThat(v.clamped(1000f, 500f, 800f, 600f)).isEqualTo(v)
    }

    @Test
    fun `scrolling down zooms out and scrolling up zooms in`() {
        assertThat(Viewport.zoomFactor(1f)).isLessThan(1f)
        assertThat(Viewport.zoomFactor(-1f)).isGreaterThan(1f)
        assertThat(Viewport.zoomFactor(0f)).isEqualTo(1f)
    }
}
