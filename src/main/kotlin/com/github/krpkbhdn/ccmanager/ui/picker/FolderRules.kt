package com.github.krpkbhdn.ccmanager.ui.picker

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** One sub-directory shown in the browser list. */
data class FolderEntry(val path: Path, val name: String, val hidden: Boolean)

/** Signals that a directory looks like a project; checked lazily per row. */
enum class ProjectBadge(val label: String) { Git("Git"), Claude("Claude"), Build("Build") }

/** Outcome of listing one directory. */
sealed interface Listing {
    data class Loaded(val folders: List<FolderEntry>) : Listing
    data object Denied : Listing
    data object Missing : Listing
}

/** Why a typed or selected path cannot be chosen. */
enum class PathCheck(val message: String?) {
    Valid(null),
    Blank("Type or paste a folder path"),
    Malformed("This is not a valid path"),
    NotFound("This path does not exist"),
    NotDirectory("This is a file, not a folder"),
    Unreadable("You do not have access to this folder"),
}

/** One clickable segment of the path bar; [path] is null for the collapsed "…" marker. */
data class Crumb(val label: String, val path: Path?)

/** Pure file-system rules of the picker: no UI state, only `java.nio.file`. */
object FolderRules {
    private const val ELLIPSIS = "…"
    private const val SUGGESTION_LIMIT = 6
    private val gitMarker = listOf(".git")
    private val claudeMarkers = listOf("CLAUDE.md", ".claude")
    private val buildMarkers = listOf(
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "package.json", "Cargo.toml",
        "go.mod", "pyproject.toml", "requirements.txt", "CMakeLists.txt", "Makefile", "composer.json", "Gemfile", "mix.exs",
    )
    private val forbiddenNameChars = "<>:\"/\\|?*".toSet()
    private val reservedNames = setOf(".", "..")

    fun checkText(text: String): PathCheck = if (text.isBlank()) PathCheck.Blank else parse(text)?.let(::check) ?: PathCheck.Malformed

    fun check(path: Path?): PathCheck = when {
        path == null -> PathCheck.Blank
        !Files.exists(path) -> PathCheck.NotFound
        !Files.isDirectory(path) -> PathCheck.NotDirectory
        !Files.isReadable(path) -> PathCheck.Unreadable
        else -> PathCheck.Valid
    }

    /** Parses typed text into a path, or null when blank or syntactically invalid. */
    fun parse(text: String): Path? = text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Path.of(it) }.getOrNull() }

    fun listFolders(dir: Path): Listing = try {
        Files.newDirectoryStream(dir).use { stream ->
            Listing.Loaded(stream.filter { Files.isDirectory(it) }.map { FolderEntry(it, it.fileName.toString(), isHidden(it)) })
        }
    } catch (_: AccessDeniedException) {
        Listing.Denied
    } catch (_: NoSuchFileException) {
        Listing.Missing
    } catch (_: IOException) {
        Listing.Denied
    }

    private fun isHidden(path: Path): Boolean = path.fileName.toString().startsWith(".") || runCatching { Files.isHidden(path) }.getOrDefault(false)

    /** Case-insensitive name order, filtered by [query] and by the hidden toggle. */
    fun visible(folders: List<FolderEntry>, showHidden: Boolean, query: String): List<FolderEntry> =
        folders.filter { (showHidden || !it.hidden) && it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun hiddenCount(folders: List<FolderEntry>): Int = folders.count { it.hidden }

    /** At most a dozen `stat` calls, so it is safe to run for every visible row. */
    fun badges(dir: Path): Set<ProjectBadge> = buildSet {
        if (gitMarker.any { exists(dir, it) }) add(ProjectBadge.Git)
        if (claudeMarkers.any { exists(dir, it) }) add(ProjectBadge.Claude)
        if (buildMarkers.any { exists(dir, it) }) add(ProjectBadge.Build)
    }

    private fun exists(dir: Path, name: String) = Files.exists(dir.resolve(name))

    fun breadcrumbs(path: Path): List<Crumb> {
        val root = path.root
        val rootCrumb = root?.let { Crumb(it.toString().trimEnd('\\', '/').ifEmpty { "/" }, it) }
        var walked: Path? = root
        val names = path.filter { it.toString().isNotEmpty() }.map { name ->
            walked = walked?.resolve(name) ?: name
            Crumb(name.toString(), walked)
        }
        return listOfNotNull(rootCrumb) + names
    }

    /** Keeps the first crumb and the last [maxVisible]-1, replacing the middle with a jump to the last hidden one. */
    fun collapse(crumbs: List<Crumb>, maxVisible: Int): List<Crumb> {
        if (crumbs.size <= maxVisible) return crumbs
        val tail = crumbs.takeLast(maxVisible - 2)
        val hiddenLast = crumbs[crumbs.size - tail.size - 1]
        return listOf(crumbs.first(), Crumb(ELLIPSIS, hiddenLast.path)) + tail
    }

    /** Child directories of the typed path's parent whose names start with what is typed after the last separator. */
    fun suggestions(text: String): List<Path> {
        val typed = text.trimStart()
        val endsWithSeparator = typed.endsWith("/") || typed.endsWith("\\")
        val parsed = parse(typed) ?: return emptyList()
        val parent = if (endsWithSeparator) parsed else parsed.parent ?: return emptyList()
        val prefix = if (endsWithSeparator) "" else parsed.fileName?.toString().orEmpty()
        val loaded = listFolders(parent) as? Listing.Loaded ?: return emptyList()
        return loaded.folders.filter { !it.hidden || prefix.startsWith(".") }
            .filter { it.name.startsWith(prefix, ignoreCase = true) && !(it.name == prefix && !endsWithSeparator) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .take(SUGGESTION_LIMIT).map { it.path }
    }

    /** Null when [name] can be created inside [parent]; otherwise the message to show. */
    fun newFolderError(parent: Path, name: String): String? = when {
        name.isBlank() -> "Enter a folder name"
        name.trim() in reservedNames -> "This name is reserved"
        name.any { it in forbiddenNameChars || it.isISOControl() } -> "Names cannot contain < > : \" / \\ | ? *"
        name.endsWith(".") || name.endsWith(" ") -> "Names cannot end with a dot or a space"
        Files.exists(parent.resolve(name.trim())) -> "A folder with this name already exists"
        else -> null
    }

    fun createFolder(parent: Path, name: String): Result<Path> = runCatching { Files.createDirectory(parent.resolve(name.trim())) }
}
