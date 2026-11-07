package com.github.krpkbhdn.ccmanager.ui.picker

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Writes through a temp file and an atomic move, so a crash or a second writer never leaves a half-written file. */
internal fun writeAtomically(file: Path, text: String) {
    Files.createDirectories(file.parent)
    val temp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
    try {
        Files.writeString(temp, text)
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temp)
    }
}
