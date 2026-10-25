package com.github.krpkbhdn.ccmanager.ui.projectsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsState
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/** One entry per page of the dialog panel; adding a project setting means adding one entry and one page. */
enum class ProjectSettingsSection(val label: String) { PLUGINS("Plugins"), MCP("MCP servers"), SKILLS("Skills") }

/** Everything one open Project settings dialog owns: the extension states of its project and the background work on [scope]. */
class ProjectSettingsState(
    val context: ExtensionContext.ForProject,
    section: ProjectSettingsSection,
    private val scope: CoroutineScope,
    val plugins: PluginsState,
    val mcp: McpState,
    val skills: SkillsState,
) {
    var section by mutableStateOf(section)

    /** True while a dialog of one of the pages is open: Esc then belongs to it, not to the settings dialog. */
    val innerDialogOpen: Boolean get() = plugins.pending != null || mcp.adding || skills.creating

    /** A CLI mutation is running; closing would drop its result, so the dialog stays until it ends. */
    val busy: Boolean get() = plugins.job != null || mcp.job != null || skills.job != null

    fun dispose() = scope.cancel()
}
