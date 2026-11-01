package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.HealthCheck
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsState
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillHost
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsState
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsSection
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsState
import kotlinx.coroutines.CoroutineScope

/** The global plugins, MCP and skills pages, plus the per-project settings dialog that edits the same extensions. */
class ExtensionStates(
    backend: Backend,
    scope: CoroutineScope,
    requestConfirm: (ConfirmRequest) -> Unit,
    notifications: NotificationsState,
    environment: ClaudeEnvironment,
) {
    val plugins = PluginsState(backend.plugins, scope, requestConfirm, notifications::toast)
    val mcp = McpState(backend.mcp, null, scope, requestConfirm, notifications::toast)
    private val skillHost = SkillHost(environment::openDirectory, environment::openInIde) { environment.ideAvailable }
    val skills = SkillsState(backend.skills, null, scope, requestConfirm, notifications::toast, skillHost)
    private val projectSettingsHost = ProjectSettingsHost(backend, scope, requestConfirm, notifications, skillHost, ::reloadGlobal)
    val projectSettings: ProjectSettingsState? get() = projectSettingsHost.state

    /** Opens the settings dialog of [project] with its own extension states, so the global pages stay untouched. */
    fun openProjectSettings(project: Project, section: ProjectSettingsSection = ProjectSettingsSection.PLUGINS) = projectSettingsHost.open(project, section)

    fun closeProjectSettings() = projectSettingsHost.close()

    fun dispose() = projectSettingsHost.dispose()

    /** The dialog may have changed what the global pages list; reload only pages already shown, without the catalogue or a health check. */
    private fun reloadGlobal() {
        if (plugins.loaded) plugins.refresh(reloadCatalog = false)
        if (mcp.loaded) mcp.refresh(withHealth = mcp.healthCheck == HealthCheck.DONE)
        if (skills.loaded) skills.refresh()
    }
}
