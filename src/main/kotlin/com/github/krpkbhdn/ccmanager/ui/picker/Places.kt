package com.github.krpkbhdn.ccmanager.ui.picker

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

enum class PlaceKind { Home, Desktop, Documents, Downloads, Work, Drive, Recent }

/** A shortcut in the left rail; [hint] is a secondary line such as free space. */
data class Place(val label: String, val path: Path, val kind: PlaceKind, val hint: String? = null)

/** The rail content, grouped the way it is drawn. */
data class PlaceGroups(val recent: List<Place>, val places: List<Place>, val drives: List<Place>)

/** Discovers rail shortcuts from the real file system; only existing directories are returned. */
object Places {
    private val standard = listOf("Desktop" to PlaceKind.Desktop, "Documents" to PlaceKind.Documents, "Downloads" to PlaceKind.Downloads)
    private val workDirectories = listOf("Work", "Projects", "dev", "source", "code", "repos", "src")
    private const val BYTES_PER_UNIT = 1024.0
    private val sizeUnits = listOf("B", "KB", "MB", "GB", "TB", "PB")

    fun quick(home: Path): List<Place> {
        val places = listOf(Place("Home", home, PlaceKind.Home)) +
            standard.map { (name, kind) -> Place(name, home.resolve(name), kind) } +
            workDirectories.map { Place(it, home.resolve(it), PlaceKind.Work) }
        return places.filter { Files.isDirectory(it.path) }.distinctBy { it.path.toString().lowercase() }
    }

    fun recent(paths: List<Path>): List<Place> =
        paths.filter { Files.isDirectory(it) }.map { Place(it.fileName?.toString() ?: it.toString(), it, PlaceKind.Recent, it.parent?.toString()) }

    /** Free space can block on removable or network drives, so call this off the UI thread. */
    fun drives(): List<Place> = FileSystems.getDefault().rootDirectories.filter { Files.isDirectory(it) }.map { root ->
        val free = runCatching { Files.getFileStore(root).usableSpace }.getOrNull()
        Place(root.toString().trimEnd('\\', '/').ifEmpty { "/" }, root, PlaceKind.Drive, free?.let { "${formatBytes(it)} free" })
    }

    fun discover(home: Path, recent: List<Path>): PlaceGroups = PlaceGroups(recent(recent), quick(home), drives())

    fun formatBytes(bytes: Long): String {
        var value = bytes.toDouble()
        var unit = 0
        while (value >= BYTES_PER_UNIT && unit < sizeUnits.lastIndex) {
            value /= BYTES_PER_UNIT
            unit++
        }
        return if (unit == 0 || value >= 100) "${value.toLong()} ${sizeUnits[unit]}" else "%.1f %s".format(value, sizeUnits[unit])
    }
}
