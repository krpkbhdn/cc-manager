package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ADD_FAILED_TITLE = "Could not add project"

/** The registered projects: the list, adding, renaming, removing and their default model; the rest of the app is told through callbacks. */
class ProjectsState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val hasRunningSessions: (projectId: Long) -> Boolean,
    private val sessionIdsOf: (projectId: Long) -> List<String>,
    private val closeSession: (projectId: Long, ccmSessionId: String) -> Unit,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val showProject: (projectId: Long) -> Unit,
    private val onRemoved: (projectId: Long) -> Unit,
    private val onLoaded: (projectIds: Set<Long>) -> Unit,
    private val reportError: (title: String, message: String) -> Unit,
    private val reportActionError: (message: String) -> Unit,
) {
    var all by mutableStateOf<List<Project>>(emptyList())
        private set
    var pickerOpen by mutableStateOf(false)
    var renaming by mutableStateOf<RenameRequest?>(null)

    fun nameOf(id: Long): String = all.firstOrNull { it.id == id }?.name ?: "Unknown project"

    fun find(id: Long): Project? = all.firstOrNull { it.id == id }

    suspend fun load() {
        val loaded = io { backend.projects.list() }
        all = loaded
        onLoaded(loaded.map { it.id }.toSet())
    }

    /** Adds the chosen directory of the folder picker (opened by setting [pickerOpen]). */
    fun addAt(directory: Path) {
        pickerOpen = false
        register(directory, showProject)
    }

    /** Adds [directory] as a project and reports its id to [onAdded] once the list is reloaded. */
    fun register(directory: Path, onAdded: (projectId: Long) -> Unit) {
        scope.launch {
            runGuarded({ reportError(ADD_FAILED_TITLE, it) }) {
                val added = io { backend.projects.add(directory) }
                load()
                onAdded(added.id)
            }
        }
    }

    fun projectAt(directory: Path): Project? {
        val target = realPathOf(directory)
        return all.firstOrNull { realPathOf(it.path) == target }
    }

    private fun realPathOf(path: Path): Path = runCatching { path.toRealPath() }.getOrDefault(path.toAbsolutePath().normalize())

    fun rename(project: Project, name: String) {
        renaming = null
        scope.launch { guarded { io { backend.projects.rename(project.id, name.trim()) }; load() } }
    }

    fun selectModel(project: Project, selection: ModelSelection) {
        scope.launch { guarded { io { backend.projects.selectModel(project.id, selection) }; load() } }
    }

    fun requestRemove(project: Project) {
        val warning = if (hasRunningSessions(project.id)) " Its running sessions will be terminated." else ""
        requestConfirm(
            ConfirmRequest(
                title = "Remove project",
                message = "Remove \"${project.name}\" from the manager? Files on disk stay untouched.$warning",
                confirmLabel = "Remove",
            ) { remove(project) },
        )
    }

    fun closeSessions(project: Project) = sessionIdsOf(project.id).forEach { closeSession(project.id, it) }

    fun remove(project: Project) = scope.launch {
        guarded {
            closeSessions(project)
            io { backend.projects.remove(project.id) }
            onRemoved(project.id)
            load()
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) = runGuarded(reportActionError, block)
}
