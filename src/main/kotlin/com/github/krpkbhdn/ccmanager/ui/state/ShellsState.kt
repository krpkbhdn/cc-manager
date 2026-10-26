package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.github.krpkbhdn.ccmanager.shell.ShellProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Detecting the installed shells and opening, labelling and closing the plain shell tabs of the projects. */
class ShellsState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val openShellsOf: (projectId: Long) -> List<String>,
    private val lastChosen: () -> String?,
    private val onChosen: (String) -> Unit,
    private val onOpened: (projectId: Long, terminalId: String) -> Unit,
    private val onClosed: (projectId: Long, terminalId: String) -> Unit,
    private val reportError: (String) -> Unit,
) {
    var available by mutableStateOf<List<ShellProfile>>(emptyList())
        private set

    private val labels = mutableStateMapOf<String, String>()

    /** The shell chosen last, else the first detected one. */
    val default: ShellProfile? get() = available.firstOrNull { it.name == lastChosen() } ?: available.firstOrNull()

    fun detect() {
        scope.launch { guarded { available = io { backend.shellDetector.detect() } } }
    }

    fun owns(terminalId: String) = terminalId in labels

    fun labelOf(terminalId: String): String = labels[terminalId].orEmpty()

    fun openDefault(projectId: Long) = default?.let { open(projectId, it) }

    fun open(projectId: Long, shell: ShellProfile) {
        scope.launch {
            guarded {
                val launched = io { backend.shellLauncher.launch(projectId, shell) }
                onChosen(shell.name)
                labels[launched.terminalId()] = uniqueLabel(shell.name, openShellsOf(projectId).map(::labelOf))
                backend.terminal.openShell(launched)
                onOpened(projectId, launched.terminalId())
                closeWhenExited(launched)
            }
        }
    }

    fun close(projectId: Long, terminalId: String) {
        labels.remove(terminalId)
        onClosed(projectId, terminalId)
        scope.launch { guarded { io { backend.terminal.close(terminalId) } } }
    }

    private fun closeWhenExited(shell: LaunchedShell) {
        scope.launch {
            io { runCatching { shell.process().waitFor() } }
            close(shell.projectId(), shell.terminalId())
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) = runGuarded(reportError, block)
}

/** "PowerShell", then "PowerShell 2", "PowerShell 3" for further tabs of the same shell. */
internal fun uniqueLabel(name: String, taken: List<String>): String =
    if (name !in taken) name else generateSequence(2) { it + 1 }.map { "$name $it" }.first { it !in taken }
