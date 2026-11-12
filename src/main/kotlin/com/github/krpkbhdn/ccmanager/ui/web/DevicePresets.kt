package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.web.DeviceCategory

/** A named device size in CSS pixels; the page's devicePixelRatio is the OS density times [deviceScaleFactor], whatever the zoom. */
data class DevicePreset(
    val name: String,
    val widthCss: Int,
    val heightCss: Int,
    val deviceScaleFactor: Double = 1.0,
    val category: DeviceCategory = DeviceCategory.of(widthCss),
)

sealed interface DeviceZoom {
    data object Fit : DeviceZoom
    data class Percent(val value: Int) : DeviceZoom
}

/** The user's viewport choice: a preset or a custom size (preset null), its zoom and orientation. */
data class DeviceSelection(
    val preset: DevicePreset? = null,
    val widthCss: Int = 0,
    val heightCss: Int = 0,
    val zoom: DeviceZoom = DeviceZoom.Fit,
    val rotated: Boolean = false,
    val fromResponsive: Boolean = false,
) {
    /** Width and height 0 mean the page fills the available area (responsive). */
    val responsive: Boolean get() = widthCss <= 0 || heightCss <= 0

    /** The page's CSS size after rotation: what the page lays out at. */
    val layoutWidth: Int get() = if (rotated) heightCss else widthCss
    val layoutHeight: Int get() = if (rotated) widthCss else heightCss

    /** Category of the preset, by the rotated layout width for a custom size, null while responsive or a size derived from it by zooming. */
    val scope: DeviceCategory? get() = if (responsive || fromResponsive) null else preset?.category ?: DeviceCategory.of(layoutWidth)

    /** A custom size typed by the user as layout width and height; a null or invalid side keeps the current one. */
    fun withLayoutSize(width: Int?, height: Int?): DeviceSelection {
        val w = width ?: layoutWidth
        val h = height ?: layoutHeight
        return custom(if (rotated) h else w, if (rotated) w else h, this)
    }

    companion object {
        val Responsive = DeviceSelection()

        val presets = listOf(
            DevicePreset("iPhone SE", 375, 667, 2.0, DeviceCategory.MOBILE),
            DevicePreset("iPhone 14", 390, 844, 3.0, DeviceCategory.MOBILE),
            DevicePreset("iPhone 15 / 16", 393, 852, 3.0, DeviceCategory.MOBILE),
            DevicePreset("iPhone 16 Pro", 402, 874, 3.0, DeviceCategory.MOBILE),
            DevicePreset("iPhone 15 Pro Max", 430, 932, 3.0, DeviceCategory.MOBILE),
            DevicePreset("iPhone 16 Pro Max", 440, 956, 3.0, DeviceCategory.MOBILE),
            DevicePreset("Galaxy S24", 360, 780, 3.0, DeviceCategory.MOBILE),
            DevicePreset("Galaxy S24 Ultra", 384, 824, 3.75, DeviceCategory.MOBILE),
            DevicePreset("Galaxy A55", 412, 915, 2.625, DeviceCategory.MOBILE),
            DevicePreset("Pixel 8", 412, 915, 2.625, DeviceCategory.MOBILE),
            DevicePreset("Pixel 8 Pro", 448, 998, 3.0, DeviceCategory.MOBILE),
            DevicePreset("iPad mini", 744, 1133, 2.0, DeviceCategory.TABLET),
            DevicePreset("iPad Air", 820, 1180, 2.0, DeviceCategory.TABLET),
            DevicePreset("iPad Pro 11", 834, 1194, 2.0, DeviceCategory.TABLET),
            DevicePreset("iPad Pro 13", 1032, 1376, 2.0, DeviceCategory.TABLET),
            DevicePreset("Galaxy Tab S9", 800, 1280, 2.0, DeviceCategory.TABLET),
            DevicePreset("HD 720p", 1280, 720, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("Laptop", 1366, 768, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("Laptop L", 1440, 900, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("Laptop HD+", 1536, 864, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("Desktop", 1920, 1080, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("Large monitor", 2560, 1440, 1.0, DeviceCategory.DESKTOP),
            DevicePreset("4K", 3840, 2160, 1.0, DeviceCategory.DESKTOP),
        )
        val presetsByCategory: Map<DeviceCategory, List<DevicePreset>> = presets.groupBy { it.category }

        fun of(preset: DevicePreset, previous: DeviceSelection = Responsive) =
            previous.copy(preset = preset, widthCss = preset.widthCss, heightCss = preset.heightCss, fromResponsive = false)

        fun custom(widthCss: Int, heightCss: Int, previous: DeviceSelection = Responsive, fromResponsive: Boolean = false) =
            previous.copy(
                rotated = previous.rotated && !previous.responsive,
                preset = null, widthCss = widthCss.coerceIn(MIN_SIDE, MAX_SIDE), heightCss = heightCss.coerceIn(MIN_SIDE, MAX_SIDE),
                fromResponsive = fromResponsive,
            )

        const val MIN_SIDE = 100
        const val MAX_SIDE = 8000
    }
}
