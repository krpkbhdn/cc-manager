package com.github.krpkbhdn.ccmanager.ui.picker

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Most-recently-chosen folders, one path per line in [file]; every IO failure degrades to "no recents". */
class RecentFolders(private val file: Path, private val limit: Int = DEFAULT_LIMIT) {

    fun load(): List<Path> = try {
        if (Files.isRegularFile(file)) Files.readAllLines(file).mapNotNull(FolderRules::parse).take(limit) else emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    /** Puts [path] first, dropping an earlier occurrence and anything beyond the limit. */
    fun add(path: Path) {
        val updated = (listOf(path) + load().filter { it != path }).take(limit)
        try {
            Files.createDirectories(file.parent)
            Files.write(file, updated.map(Path::toString))
        } catch (_: IOException) {
            // Recents are a convenience; failing to save must never block choosing a folder.
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 8
        private const val FILE_NAME = "recent-folders.txt"

        fun inDataDir(dataDir: Path): RecentFolders = RecentFolders(dataDir.resolve(FILE_NAME))
    }
}
