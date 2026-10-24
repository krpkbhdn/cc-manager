package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Hoisted state of [FolderPickerDialog]: navigation, listing, selection and the two inline editors. */
class FolderPickerState(initial: Path?, private val scope: CoroutineScope, private val recents: RecentFolders) {
    private val home: Path = Path.of(System.getProperty("user.home"))
    private var loadJob: Job? = null
    private var typeBuffer = ""
    private var lastTypedAt = 0L

    var history by mutableStateOf(NavigationHistory(initial?.takeIf { Files.isDirectory(it) } ?: home))
        private set
    var listing by mutableStateOf<Listing?>(null)
        private set
    var places by mutableStateOf(PlaceGroups(emptyList(), Places.quick(home), emptyList()))
        private set
    var showHidden by mutableStateOf(false)
    var highlighted by mutableStateOf<Path?>(null)
        private set
    var editingPath by mutableStateOf(false)
        private set
    var creatingFolder by mutableStateOf(false)
        private set
    var newFolderError by mutableStateOf<String?>(null)
        private set
    val filter = TextFieldState()
    val pathText = TextFieldState()
    val newFolderName = TextFieldState()
    val badges = mutableStateMapOf<Path, Set<ProjectBadge>>()

    val current: Path get() = history.current
    val target: Path get() = highlighted ?: current

    init {
        load(current, select = null)
        scope.launch { places = withContext(Dispatchers.IO) { Places.discover(home, recents.load()) } }
    }

    fun open(path: Path) = go(history.visit(path), select = null)

    fun back() = go(history.back(), select = null)

    fun forward() = go(history.forward(), select = null)

    fun up() {
        val previous = current
        go(history.up(), select = previous)
    }

    private fun go(next: NavigationHistory, select: Path?) {
        history = next
        editingPath = false
        creatingFolder = false
        filter.clearText()
        load(next.current, select)
    }

    fun reload(select: Path? = highlighted) = load(current, select)

    private fun load(dir: Path, select: Path?) {
        loadJob?.cancel()
        listing = null
        highlighted = null
        loadJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) { FolderRules.listFolders(dir) }
            listing = loaded
            highlighted = select?.takeIf { wanted -> (loaded as? Listing.Loaded)?.folders?.any { it.path == wanted } == true }
        }
    }

    /** Dismisses the innermost transient editor; false when nothing was open and the dialog itself should close. */
    fun escape(): Boolean = when {
        creatingFolder -> true.also { creatingFolder = false }
        editingPath -> true.also { editingPath = false }
        filter.text.isNotEmpty() -> true.also { filter.clearText() }
        else -> false
    }

    fun select(path: Path?) {
        highlighted = path
    }

    fun startEditingPath(seed: String = withSeparator(current)) {
        editingPath = true
        pathText.setTextAndPlaceCursorAtEnd(seed)
    }

    fun stopEditingPath() {
        editingPath = false
    }

    /** Jumps to the typed path when it is a readable directory; returns the failure otherwise. */
    fun commitTypedPath(): PathCheck {
        val parsed = FolderRules.parse(pathText.text.toString())
        val check = FolderRules.checkText(pathText.text.toString())
        if (check == PathCheck.Valid && parsed != null) open(parsed)
        return check
    }

    fun moveHighlight(delta: Int, rows: List<FolderEntry>) {
        if (rows.isEmpty()) return
        val index = rows.indexOfFirst { it.path == highlighted }
        val next = if (index < 0) (if (delta > 0) 0 else rows.lastIndex) else (index + delta).coerceIn(0, rows.lastIndex)
        highlighted = rows[next].path
    }

    /** Returns the row index to scroll to, or null when nothing matches the typed prefix. */
    fun typeAhead(char: Char, rows: List<FolderEntry>, nowMillis: Long): Int? {
        typeBuffer = if (nowMillis - lastTypedAt > TYPE_AHEAD_RESET_MILLIS) char.toString() else typeBuffer + char
        lastTypedAt = nowMillis
        val index = rows.indexOfFirst { it.name.startsWith(typeBuffer, ignoreCase = true) }
        if (index >= 0) highlighted = rows[index].path
        return index.takeIf { it >= 0 }
    }

    fun startCreatingFolder() {
        creatingFolder = true
        newFolderError = null
        newFolderName.clearText()
    }

    fun cancelCreatingFolder() {
        creatingFolder = false
    }

    fun validateNewFolder() {
        val name = newFolderName.text.toString()
        newFolderError = if (name.isEmpty()) null else FolderRules.newFolderError(current, name)
    }

    fun createFolder() {
        val name = newFolderName.text.toString()
        val error = FolderRules.newFolderError(current, name)
        if (error != null) {
            newFolderError = error
            return
        }
        scope.launch {
            val created = withContext(Dispatchers.IO) { FolderRules.createFolder(current, name) }
            created.onSuccess {
                creatingFolder = false
                reload(select = it)
            }.onFailure { newFolderError = "Could not create the folder: ${it.message}" }
        }
    }

    fun recordChoice(path: Path) = recents.add(path)

    fun cacheBadges(path: Path, found: Set<ProjectBadge>) {
        badges[path] = found
    }

    private fun withSeparator(path: Path): String = path.toString().let { if (it.endsWith(path.fileSystem.separator)) it else it + path.fileSystem.separator }

    private companion object {
        const val TYPE_AHEAD_RESET_MILLIS = 900L
    }
}
