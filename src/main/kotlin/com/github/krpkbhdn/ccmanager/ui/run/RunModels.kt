package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.foundation.text.input.TextFieldState
import com.github.krpkbhdn.ccmanager.run.ActiveRun
import com.github.krpkbhdn.ccmanager.run.RunConfiguration
import java.nio.file.Path

private const val PROJECT_ROOT = "project root"

/** A run configuration with what the UI shows about it; [run] is its latest run, kept after the process exits. */
data class RunEntry(val configuration: RunConfiguration, val directoryHint: String, val directoryMissing: Boolean, val run: ActiveRun?) {
    val id: Long get() = configuration.id()
    val name: String get() = configuration.name()
    val running: Boolean get() = run?.running() == true
    val exitCode: Int? get() = run?.exitCode()?.takeIf { it.isPresent }?.asInt
    val runId: String? get() = run?.runId()
}

/** The directory relative to the project folder for display: `project root`, `.claude/worktrees/x`, or the absolute path outside it. */
fun directoryHint(project: Path, directory: Path): String {
    val target = directory.toAbsolutePath().normalize()
    val root = project.toAbsolutePath().normalize()
    return when {
        target == root -> PROJECT_ROOT
        target.startsWith(root) -> root.relativize(target).joinToString("/")
        else -> target.toString()
    }
}

/** Input of the create/edit dialog; [existing] is null for a new configuration. */
class RunDialogState(val projectId: Long, val existing: RunConfiguration?, projectPath: Path) {
    val name = TextFieldState(existing?.name().orEmpty())
    val command = TextFieldState(existing?.command().orEmpty())
    val directory = TextFieldState((existing?.directory() ?: projectPath).toString())
    val hasInput: Boolean get() = name.text.isNotEmpty() || command.text.isNotEmpty()
}
