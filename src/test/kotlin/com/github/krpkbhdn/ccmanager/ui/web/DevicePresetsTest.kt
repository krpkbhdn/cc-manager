package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DevicePresetsTest {
    @Test
    fun `preset categories match the width rule except tablets above the desktop threshold`() {
        DeviceSelection.presets.forEach {
            if (it.name != "iPad Pro 13") assertThat(it.category).describedAs(it.name).isEqualTo(DeviceCategory.of(it.widthCss))
        }
    }

    @Test
    fun `thresholds split mobile tablet desktop`() {
        assertThat(DeviceCategory.of(599)).isEqualTo(DeviceCategory.MOBILE)
        assertThat(DeviceCategory.of(600)).isEqualTo(DeviceCategory.TABLET)
        assertThat(DeviceCategory.of(1023)).isEqualTo(DeviceCategory.TABLET)
        assertThat(DeviceCategory.of(1024)).isEqualTo(DeviceCategory.DESKTOP)
    }

    @Test
    fun `presets are grouped into all three categories`() {
        assertThat(DeviceSelection.presetsByCategory.keys).containsExactlyInAnyOrder(*DeviceCategory.entries.toTypedArray())
        assertThat(DeviceSelection.presets.first { it.name == "iPad Pro 13" }.category).isEqualTo(DeviceCategory.TABLET)
    }

    @Test
    fun `scope is null when responsive, by width when custom, the preset category otherwise`() {
        assertThat(DeviceSelection.Responsive.scope).isNull()
        assertThat(DeviceSelection.custom(500, 800).scope).isEqualTo(DeviceCategory.MOBILE)
        assertThat(DeviceSelection.custom(900, 800).scope).isEqualTo(DeviceCategory.TABLET)
        assertThat(DeviceSelection.custom(1500, 800).scope).isEqualTo(DeviceCategory.DESKTOP)
        assertThat(DeviceSelection.of(DeviceSelection.presets.first { it.name == "iPad mini" }).scope).isEqualTo(DeviceCategory.TABLET)
    }

    @Test
    fun `scope of a rotated custom size uses the rotated layout width`() {
        val rotated = DeviceSelection.custom(500, 1200).copy(rotated = true)

        assertThat(rotated.layoutWidth).isEqualTo(1200)
        assertThat(rotated.scope).isEqualTo(DeviceCategory.DESKTOP)
    }

    @Test
    fun `switching from responsive to custom never starts rotated`() {
        val staleResponsive = DeviceSelection.Responsive.copy(rotated = true)

        val custom = DeviceSelection.custom(800, 600, staleResponsive)

        assertThat(custom.layoutWidth).isEqualTo(800)
        assertThat(custom.layoutHeight).isEqualTo(600)
    }

    @Test
    fun `a custom size typed as layout size keeps the rotation of a custom selection`() {
        val rotated = DeviceSelection.custom(800, 600).copy(rotated = true)

        val resized = rotated.withLayoutSize(700, 500)

        assertThat(resized.rotated).isTrue()
        assertThat(resized.layoutWidth).isEqualTo(700)
        assertThat(resized.layoutHeight).isEqualTo(500)
    }

    @Test
    fun `an invalid side keeps the current one and an out of range one is clamped`() {
        val size = DeviceSelection.custom(800, 600)

        assertThat(size.withLayoutSize(null, 500).layoutWidth).isEqualTo(800)
        assertThat(size.withLayoutSize(null, 500).layoutHeight).isEqualTo(500)
        assertThat(size.withLayoutSize(50, null).layoutWidth).isEqualTo(DeviceSelection.MIN_SIDE)
        assertThat(size.withLayoutSize(null, null)).isEqualTo(size)
    }
}
