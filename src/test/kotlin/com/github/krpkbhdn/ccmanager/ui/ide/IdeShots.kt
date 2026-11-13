package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.state.IdeMenuActions
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.workspace.InstalledIde
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.EncodedImageFormat

private const val FRAME_NANOS = 16_000_000L
private const val FRAMES = 20
private const val WIDTH = 420
private const val HEIGHT = 330

/** Real executables so the system icon shows; the last IDE has none and shows the fallback glyph. */
internal val sampleIdes = listOf(
    InstalledIde("a", "Explorer IDE", listOf("C:/Windows/explorer.exe")),
    InstalledIde("b", "Notepad IDE", listOf("C:/Windows/notepad.exe")),
    InstalledIde("c", "No icon IDE", listOf("missing-tool")),
)

internal fun sampleIcons(): Map<String, ImageBitmap> = sampleIdes.mapNotNull { ide -> IdeIconExtractor().extract(ide)?.let { ide.id to it } }.toMap()

/** Renders the IDE button and menu offscreen. `--out=<dir> --dark|--light`. */
fun main(args: Array<String>) {
    val out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: "build/ccm-shots/ide")
    val themes = when { "--dark" in args -> listOf(true); "--light" in args -> listOf(false); else -> listOf(true, false) }
    Files.createDirectories(out)
    val icons = sampleIcons()
    themes.forEach { dark ->
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) { AppTheme(dark) { IdeMenuScene(sampleIdes, sampleIdes[0], icons, IdeMenuActions({}, {}, {}, {})) } }
        repeat(FRAMES) { scene.render(it * FRAME_NANOS) }
        val file = out.resolve("ide-menu-${if (dark) "dark" else "light"}.png")
        Files.write(file, scene.render(FRAMES * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("wrote ${file.toAbsolutePath()} icons=${icons.keys}")
        scene.close()
    }
    kotlin.system.exitProcess(0)
}
