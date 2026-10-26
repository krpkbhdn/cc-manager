package com.github.krpkbhdn.ccmanager.ui.picker

import java.awt.Rectangle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Position and size of the picker window in AWT units; [x] and [y] are null until the user ever moved it. */
data class PickerBounds(val x: Int?, val y: Int?, val width: Int, val height: Int)

/** Remembers the picker window bounds as "x y width height" in [file]; every IO failure degrades to the defaults. */
class PickerWindowStore(private val file: Path) {

    fun load(): PickerBounds? = try {
        if (Files.isRegularFile(file)) parse(Files.readString(file)) else null
    } catch (_: IOException) {
        null
    }

    fun save(bounds: PickerBounds) {
        if (bounds.x == null || bounds.y == null) return
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, "${bounds.x} ${bounds.y} ${bounds.width} ${bounds.height}")
        } catch (_: IOException) {
            // Window bounds are a convenience; failing to save must never block the picker.
        }
    }

    private fun parse(text: String): PickerBounds? {
        val parts = text.trim().split(' ').map { it.toIntOrNull() ?: return null }
        return if (parts.size == PART_COUNT) PickerBounds(parts[0], parts[1], parts[2], parts[3]) else null
    }

    companion object {
        const val MIN_WIDTH = 560
        const val MIN_HEIGHT = 420
        const val DEFAULT_WIDTH = 860
        const val DEFAULT_HEIGHT = 600
        private const val VISIBLE_MARGIN = 120
        private const val PART_COUNT = 4
        private const val FILE_NAME = "picker-window.txt"

        fun inDataDir(dataDir: Path): PickerWindowStore = PickerWindowStore(dataDir.resolve(FILE_NAME))

        /** Keeps the size within the minimum and the biggest screen, and the title bar reachable on the screen the window mostly sits on. */
        fun clamp(bounds: PickerBounds, screens: List<Rectangle>): PickerBounds {
            val screen = screens.maxByOrNull { overlap(bounds, it) } ?: return bounds
            val width = bounds.width.coerceIn(MIN_WIDTH, maxOf(MIN_WIDTH, screen.width))
            val height = bounds.height.coerceIn(MIN_HEIGHT, maxOf(MIN_HEIGHT, screen.height))
            if (bounds.x == null || bounds.y == null) return PickerBounds(null, null, width, height)
            val x = bounds.x.coerceIn(screen.x - width + VISIBLE_MARGIN, screen.x + screen.width - VISIBLE_MARGIN)
            val y = bounds.y.coerceIn(screen.y, screen.y + screen.height - VISIBLE_MARGIN)
            return PickerBounds(x, y, width, height)
        }

        private fun overlap(bounds: PickerBounds, screen: Rectangle): Int {
            val rect = Rectangle(bounds.x ?: screen.x, bounds.y ?: screen.y, bounds.width, bounds.height).intersection(screen)
            return if (rect.isEmpty) 0 else rect.width * rect.height
        }
    }
}
