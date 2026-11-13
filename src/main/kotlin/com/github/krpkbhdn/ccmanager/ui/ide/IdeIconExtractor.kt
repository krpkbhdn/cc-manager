package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.krpkbhdn.ccmanager.ui.state.ideIconCandidates
import com.github.krpkbhdn.ccmanager.workspace.InstalledIde
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileSystemView

private const val ICON_PIXELS = 32

/** Reads the icon the operating system shows for an IDE's executable; blocking, call it off the UI thread. */
class IdeIconExtractor(private val osName: String = System.getProperty("os.name"), private val home: Path = Path.of(System.getProperty("user.home"))) {
    fun extract(ide: InstalledIde): ImageBitmap? = ideIconCandidates(ide.command, osName, home).firstOrNull(Files::exists)?.let(::systemIcon)

    private fun systemIcon(file: Path): ImageBitmap? = try {
        val icon = FileSystemView.getFileSystemView().getSystemIcon(file.toFile(), ICON_PIXELS, ICON_PIXELS)
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().also { icon.paintIcon(null, it, 0, 0); it.dispose() }
        image.toComposeImageBitmap()
    } catch (e: RuntimeException) {
        null
    }
}
