package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset as Tol
import org.junit.jupiter.api.Test

class RegionSelectionTest {
    private val flat = PageViewport(800, 600)
    private val tol = Tol.offset(0.01)

    private fun assertRect(r: CssRect, x: Double, y: Double, w: Double, h: Double) {
        assertThat(r.x).isCloseTo(x, tol)
        assertThat(r.y).isCloseTo(y, tol)
        assertThat(r.width).isCloseTo(w, tol)
        assertThat(r.height).isCloseTo(h, tol)
    }

    @Test
    fun forwardDragGivesTheRectangle() = assertRect(RegionSelection.cssRect(flat, Offset(10f, 20f), Offset(110f, 70f)), 10.0, 20.0, 100.0, 50.0)

    @Test
    fun reverseDragIsNormalised() = assertRect(RegionSelection.cssRect(flat, Offset(110f, 70f), Offset(10f, 20f)), 10.0, 20.0, 100.0, 50.0)

    @Test
    fun dragIsClampedToThePage() = assertRect(RegionSelection.cssRect(flat, Offset(-50f, 500f), Offset(900f, 700f)), 0.0, 500.0, 800.0, 100.0)

    @Test
    fun zoomedAndPannedViewportMapsToCss() {
        val v = PageViewport(1000, 2000, scale = 0.5, offsetX = -100.0, offsetY = -40.0)
        assertRect(RegionSelection.cssRect(v, Offset(0f, 0f), Offset(100f, 50f)), 200.0, 80.0, 200.0, 100.0)
    }

    @Test
    fun centeredRotatedDeviceClampsToItsPage() {
        val landscape = PageViewport(844, 390, scale = 0.5, offsetX = 50.0, offsetY = 20.0)
        assertRect(RegionSelection.cssRect(landscape, Offset(0f, 0f), Offset(300f, 100f)), 0.0, 0.0, 500.0, 160.0)
    }

    @Test
    fun smallRectangleIsNotUsable() {
        assertThat(RegionSelection.cssRect(flat, Offset(5f, 5f), Offset(12f, 40f)).isUsable()).isFalse()
        assertThat(RegionSelection.cssRect(flat, Offset(5f, 5f), Offset(13f, 13f)).isUsable()).isTrue()
    }

    @Test
    fun cssToCanvasRoundTrips() {
        val v = PageViewport(1000, 2000, scale = 0.75, offsetX = -30.0, offsetY = 12.0)
        val css = CssRect(100.0, 200.0, 300.0, 150.0)
        val canvas = RegionSelection.canvasRect(v, css)
        assertRect(RegionSelection.cssRect(v, canvas.topLeft, canvas.bottomRight), 100.0, 200.0, 300.0, 150.0)
    }

    @Test
    fun labelUsesAPlainX() = assertThat(RegionSelection.label(CssRect(0.0, 0.0, 320.9, 48.0))).isEqualTo("320 x 48")
}
