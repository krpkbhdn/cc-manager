package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val JUST_NOW_SECONDS = 10L
private const val SHORT_ID_LENGTH = 6
private const val PATH_TAIL_SEGMENTS = 2

/** "42s", "4m 05s", "2h 10m": compact elapsed time for live labels. */
fun formatElapsed(elapsed: Duration): String {
    val seconds = elapsed.seconds.coerceAtLeast(0)
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> "${hours}h ${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}m"
        minutes > 0 -> "${minutes}m ${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}

fun relativeTime(at: Instant, now: Instant): String {
    val seconds = Duration.between(at, now).seconds
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        seconds < JUST_NOW_SECONDS -> "just now"
        minutes < 1 -> "${seconds}s ago"
        hours < 1 -> "${minutes}m ago"
        days < 1 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

fun statusLabel(status: AgentStatus): String = when (status) {
    AgentStatus.OFFLINE -> "Offline"
    AgentStatus.IDLE -> "Free"
    AgentStatus.WORKING -> "Working"
    AgentStatus.NEEDS_ACTION -> "Needs action"
}

fun backgroundTasksLabel(count: Int): String = if (count == 1) "1 background task" else "$count background tasks"

fun sessionTitle(session: SessionSnapshot): String =
    session.title()?.takeIf { it.isNotBlank() } ?: "Session ${session.ccmSessionId().take(SHORT_ID_LENGTH)}"

/** Last path segments, e.g. "Work/cc-manager", for a compact subtitle. */
fun pathTail(path: Path, segments: Int = PATH_TAIL_SEGMENTS): String {
    val names = path.map { it.toString() }
    return names.takeLast(segments).joinToString("/").ifEmpty { path.toString() }
}

private val permissionModeLabels = mapOf(
    "plan" to "Plan",
    "acceptEdits" to "Accept edits",
    "bypassPermissions" to "Bypass",
    "auto" to "Auto",
    "dontAsk" to "Don't ask",
    "default" to "Default",
)

fun permissionModeLabel(mode: String?): String? = mode?.let { permissionModeLabels[it] ?: it }

fun changeLabel(change: GitDiff.ChangeType): String = when (change) {
    GitDiff.ChangeType.ADDED -> "Added"
    GitDiff.ChangeType.MODIFIED -> "Modified"
    GitDiff.ChangeType.DELETED -> "Deleted"
    GitDiff.ChangeType.RENAMED -> "Renamed"
    GitDiff.ChangeType.UNTRACKED -> "New file"
}

fun fileName(path: String): String = path.substringAfterLast('/')

fun parentDirectory(path: String): String = path.substringBeforeLast('/', "")

private val osInstallCommands = listOf(
    "macOS / Linux" to "curl -fsSL https://claude.ai/install.sh | bash",
    "Windows (PowerShell)" to "irm https://claude.ai/install.ps1 | iex",
)

/** Install commands with the one matching [osName] first. */
fun installCommands(osName: String): List<Pair<String, String>> {
    val windows = osName.startsWith("Windows", ignoreCase = true)
    return if (windows) osInstallCommands.reversed() else osInstallCommands
}
