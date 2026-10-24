package com.github.krpkbhdn.ccmanager.ui.state

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser

private const val CHOOSER_TITLE = "Choose a project folder"
private const val MAC_DIRECTORY_PROPERTY = "apple.awt.fileDialogForDirectories"

/** Native folder picker; blocks until the user decides, so call it off the UI thread. */
object DirectoryChooser {
    fun choose(): Path? =
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) chooseOnMac() else chooseWithSwing()

    private fun chooseOnMac(): Path? {
        System.setProperty(MAC_DIRECTORY_PROPERTY, "true")
        try {
            val dialog = FileDialog(null as Frame?, CHOOSER_TITLE, FileDialog.LOAD)
            dialog.isVisible = true
            return dialog.file?.let { Path.of(dialog.directory, it) }
        } finally {
            System.setProperty(MAC_DIRECTORY_PROPERTY, "false")
        }
    }

    private fun chooseWithSwing(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = CHOOSER_TITLE
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }
}
