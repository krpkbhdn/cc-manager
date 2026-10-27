package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.run.RunConfiguration
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService
import com.github.krpkbhdn.ccmanager.run.RunService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Run configurations of the projects shown so far and their latest runs. Claude may add or delete configurations at any
 * moment, so every backend change callback reloads the lists; all state writes happen on [scope] (the UI dispatcher).
 */
class RunState(
    private val configurations: RunConfigurationService,
    private val runs: RunService,
    private val terminal: TerminalPort,
    private val scope: CoroutineScope,
    private val projectPath: (Long) -> Path?,
    private val showRunTab: (Long) -> Unit,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val reportError: (String) -> Unit,
) {
    var dialog by mutableStateOf<RunDialogState?>(null)
        private set

    private val entries = mutableStateMapOf<Long, List<RunEntry>>()
    private val selection = mutableStateMapOf<Long, Long>()
    private val consoles = mutableMapOf<Long, String>()
    private val unsubscribers = mutableListOf<Runnable>()
    private val reload = Refresher(scope) { runGuarded(reportError) { load() } }

    fun start() {
        unsubscribers += configurations.subscribe { reload.request() }
        unsubscribers += runs.subscribe { reload.request() }
    }

    fun dispose() {
        unsubscribers.forEach { it.run() }
        unsubscribers.clear()
    }

    /** Starts tracking [projectId] and reloads; the list appears after the load. */
    fun track(projectId: Long) {
        if (projectId !in entries) entries[projectId] = emptyList()
        reload.request()
    }

    fun retainProjects(projectIds: Set<Long>) {
        entries.keys.retainAll(projectIds)
        selection.keys.retainAll(projectIds)
    }

    fun entriesOf(projectId: Long): List<RunEntry> = entries[projectId].orEmpty()

    /** The chosen configuration, falling back to the first one when it was deleted or never chosen. */
    fun selectedOf(projectId: Long): RunEntry? = entriesOf(projectId).let { list -> list.firstOrNull { it.id == selection[projectId] } ?: list.firstOrNull() }

    fun select(projectId: Long, configurationId: Long) {
        selection[projectId] = configurationId
    }

    fun run(configurationId: Long) {
        scope.launch {
            runGuarded(reportError) {
                val session = io { runs.start(configurationId) }
                select(session.projectId(), configurationId)
                showConsole(configurationId, session)
                showRunTab(session.projectId())
                reload.request()
            }
        }
    }

    fun stopRunning(projectId: Long) = entriesOf(projectId).filter { it.running }.forEach { stop(it.id) }

    fun stop(configurationId: Long) {
        scope.launch { runGuarded(reportError) { io { runs.stop(configurationId) } } }
    }

    fun openDialog(projectId: Long, existing: RunConfiguration? = null) {
        projectPath(projectId)?.let { dialog = RunDialogState(projectId, existing, it) }
    }

    fun closeDialog() {
        dialog = null
    }

    fun save(form: RunDialogState, onError: (String) -> Unit) {
        scope.launch {
            runGuarded(onError) {
                val name = form.name.text.toString()
                val command = form.command.text.toString()
                val directory = Path.of(form.directory.text.toString().trim())
                val saved = io { form.existing?.let { configurations.update(it.id(), name, command, directory) } ?: configurations.save(form.projectId, name, command, directory) }
                select(form.projectId, saved.id())
                dialog = null
                reload.request()
            }
        }
    }

    fun requestDelete(configuration: RunConfiguration) {
        requestConfirm(
            ConfirmRequest("Delete run configuration", "\"${configuration.name()}\" is removed and its run is stopped.", "Delete") {
                dialog = null
                scope.launch { runGuarded(reportError) { io { configurations.delete(configuration.id()) } } }
            },
        )
    }

    private suspend fun load() {
        val tracked = entries.keys.associateWith { projectPath(it) }
        val loaded = io { tracked.mapValues { (id, path) -> path?.let { entriesOf(id, it) }.orEmpty() } }
        loaded.forEach { (id, list) -> entries[id] = list }
        syncConsoles(loaded.values.flatten())
    }

    private fun entriesOf(projectId: Long, path: Path): List<RunEntry> = configurations.list(projectId).map { config ->
        RunEntry(config, directoryHint(path, config.directory()), !Files.isDirectory(config.directory()), runs.current(config.id()).orElse(null))
    }

    /** Runs started by someone else (Claude) get their console here; consoles of replaced or deleted runs are closed. */
    private fun syncConsoles(all: List<RunEntry>) {
        all.forEach { entry -> entry.run?.let { showConsole(entry.id, LaunchedSession(it.runId(), entry.configuration.projectId(), it.label(), it.process())) } }
        val live = all.filter { it.run != null }.map { it.id }.toSet()
        consoles.keys.filter { it !in live }.forEach { closeConsole(consoles.remove(it)) }
    }

    private fun showConsole(configurationId: Long, session: LaunchedSession) {
        if (consoles[configurationId] == session.ccmSessionId()) return
        terminal.open(session)
        closeConsole(consoles.put(configurationId, session.ccmSessionId()))
    }

    private fun closeConsole(runId: String?) {
        if (runId != null) scope.launch { runGuarded(reportError) { io { terminal.close(runId) } } }
    }
}
