package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PageViewportTest {
    private val desktop = DeviceSelection.of(DeviceSelection.presets.first { it.name == "Desktop" })

    @Test
    fun `responsive fills the canvas at scale one`() {
        val v = PageViewport.of(DeviceSelection.Responsive, 800.0, 600.0)
        assertThat(v).isEqualTo(PageViewport(800, 600))
    }

    @Test
    fun `fit shrinks to the smaller ratio and centers`() {
        val v = PageViewport.of(desktop, 960.0, 800.0)
        assertThat(v.scale).isEqualTo(0.5)
        assertThat(v.offsetX).isEqualTo(0.0)
        assertThat(v.offsetY).isEqualTo((800 - 540) / 2.0)
    }

    @Test
    fun `fit never upscales unless asked`() {
        val small = DeviceSelection.custom(360, 640)
        assertThat(PageViewport.of(small, 2000.0, 2000.0).scale).isEqualTo(1.0)
        assertThat(PageViewport.fit(360, 640, 720.0, 1280.0, allowUpscale = true)).isEqualTo(2.0)
    }

    @Test
    fun `rotation swaps width and height`() {
        val v = PageViewport.of(desktop.copy(rotated = true, zoom = DeviceZoom.Percent(50)), 2000.0, 2000.0)
        assertThat(v.widthCss).isEqualTo(1080)
        assertThat(v.heightCss).isEqualTo(1920)
        assertThat(v.scale).isEqualTo(0.5)
    }

    @Test
    fun `a pan stored before rotating is clamped to the swapped page`() {
        val v = PageViewport.of(desktop.copy(rotated = true, zoom = DeviceZoom.Percent(100)), 900.0, 700.0, 5000.0, 5000.0)
        assertThat(v.panX).isEqualTo(v.maxPanX)
        assertThat(v.panY).isEqualTo(v.maxPanY)
        assertThat(v.offsetX).isEqualTo(-v.maxPanX)
    }

    @Test
    fun `canvas and page points round trip`() {
        val v = PageViewport.of(desktop.copy(zoom = DeviceZoom.Percent(75)), 2000.0, 1200.0)
        val page = Offset(300f, 200f)
        val back = v.toPage(v.toCanvas(page))
        assertThat(back.x).isCloseTo(page.x, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(back.y).isCloseTo(page.y, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(v.containsCanvas(v.toCanvas(page))).isTrue()
    }

    @Test
    fun `frame pixels are capped for a 4K page and ignore the zoom`() {
        val v = PageViewport(3840, 2160, 0.3)
        val render = v.renderScale(2f)
        assertThat(3840 * 2160 * render * render).isLessThanOrEqualTo(PageViewport.MAX_FRAME_PIXELS + 1)
        assertThat(PageViewport(1280, 720, 0.5).renderScale(1f)).isEqualTo(1.0)
        assertThat(PageViewport(1280, 720, 0.5, deviceScaleFactor = 2.0).renderScale(1.5f)).isEqualTo(3.0)
    }

    @Test
    fun `zoom above the fit is kept and the page pans within its bounds`() {
        val big = DeviceSelection.of(DeviceSelection.presets.first { it.name == "4K" }, DeviceSelection.Responsive).copy(zoom = DeviceZoom.Percent(100))
        val v = PageViewport.of(big, 1000.0, 800.0, panX = 500.0, panY = 99999.0)
        assertThat(v.scale).isEqualTo(1.0)
        assertThat(v.maxPanX).isEqualTo(2840.0)
        assertThat(v.panX).isEqualTo(500.0)
        assertThat(v.offsetX).isEqualTo(-500.0)
        assertThat(v.panY).isEqualTo(1360.0)
        assertThat(PageViewport.of(big, 1000.0, 800.0, panX = -50.0).offsetX).isEqualTo(0.0)
    }

    @Test
    fun `canvas and page points round trip with a pan`() {
        val big = desktop.copy(zoom = DeviceZoom.Percent(150))
        val v = PageViewport.of(big, 1000.0, 800.0, panX = 700.0, panY = 300.0)
        val page = Offset(900f, 400f)
        assertThat(v.toCanvas(page)).isEqualTo(Offset(650f, 300f))
        assertThat(v.toPage(v.toCanvas(page))).isEqualTo(page)
        assertThat(v.containsCanvas(Offset(10f, 10f))).isTrue()
    }

    @Test
    fun `a page smaller than the canvas stays centered whatever the pan`() {
        val v = PageViewport.of(desktop.copy(zoom = DeviceZoom.Percent(50)), 2000.0, 1200.0, panX = 300.0, panY = 300.0)
        assertThat(v.maxPanX).isZero()
        assertThat(v.offsetX).isEqualTo(520.0)
        assertThat(v.offsetY).isEqualTo(330.0)
    }

    @Test
    fun `zoom at the cursor keeps the page point under it`() {
        val before = PageViewport.of(desktop.copy(zoom = DeviceZoom.Percent(100)), 1000.0, 800.0, panX = 200.0, panY = 100.0)
        val cursor = Offset(300f, 250f)
        val pagePoint = before.toPage(cursor)
        val change = before.zoomAt(cursor, 2)
        assertThat(change.percent).isEqualTo(150)
        val after = PageViewport.of(desktop.copy(zoom = DeviceZoom.Percent(change.percent)), 1000.0, 800.0, change.panX, change.panY)
        val moved = after.toCanvas(pagePoint)
        assertThat(moved.x).isCloseTo(cursor.x, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(moved.y).isCloseTo(cursor.y, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `two pans from one stale viewport accumulate through the stored pan`() {
        val v = PageViewport.of(desktop.copy(zoom = DeviceZoom.Percent(100)), 1000.0, 800.0)
        val first = v.panned(Offset.Zero, 30.0, 10.0)
        val second = v.panned(first, 20.0, 5.0)
        assertThat(second).isEqualTo(Offset(50f, 15f))
        assertThat(v.panned(second, 99999.0, -99999.0)).isEqualTo(Offset(v.maxPanX.toFloat(), 0f))
    }

    @Test
    fun `the first drag after a zoom at the edge moves the page at once`() {
        val zoomed = desktop.copy(zoom = DeviceZoom.Percent(100))
        val before = PageViewport.of(zoomed.copy(zoom = DeviceZoom.Percent(75)), 1000.0, 800.0, panX = 0.0, panY = 0.0)
        val change = before.zoomAt(Offset(900f, 700f), 1)
        val stored = PageViewport.panAfterZoom(zoomed, change, 1000.0, 800.0)
        val after = PageViewport.of(zoomed, 1000.0, 800.0, stored.x.toDouble(), stored.y.toDouble())
        assertThat(stored.x).isLessThanOrEqualTo(after.maxPanX.toFloat())
        assertThat(after.panX.toFloat()).isEqualTo(stored.x)
        val dragged = after.panned(stored, -10.0, -10.0)
        assertThat(dragged.x).isEqualTo(stored.x - 10f)
        assertThat(dragged.y).isEqualTo(stored.y - 10f)
    }

    @Test
    fun `zooming a responsive size keeps the feedback scope showing everything`() {
        val custom = DeviceSelection.custom(500, 800, DeviceSelection.Responsive, fromResponsive = true)
        assertThat(custom.scope).isNull()
        assertThat(DeviceSelection.custom(500, 800, custom).scope).isNotNull()
    }

    @Test
    fun `zoom steps stop at the ends of the range`() {
        val v = PageViewport(1920, 1080, 4.0)
        assertThat(v.zoomAt(Offset.Zero, 1).percent).isEqualTo(400)
        assertThat(PageViewport(1920, 1080, 0.25).zoomAt(Offset.Zero, -1).percent).isEqualTo(25)
        assertThat(PageViewport(1920, 1080, 0.6).zoomAt(Offset.Zero, 1).percent).isEqualTo(67)
    }

    @Test
    fun `render scale follows the zoom above the device ratio and is capped`() {
        assertThat(PageViewport(1280, 720, 0.5).renderScale(1f)).isEqualTo(1.0)
        assertThat(PageViewport(1280, 720, 1.0).renderScale(1f)).isEqualTo(1.0)
        assertThat(PageViewport(1280, 720, 2.0).renderScale(1f)).isEqualTo(2.0)
        assertThat(PageViewport(1280, 720, 4.0).renderScale(1f)).isEqualTo(4.0)
        assertThat(PageViewport(1920, 1080, 4.0).renderScale(1f)).isLessThan(4.0)
    }
}
