package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsState
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillHost
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsState
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsSection
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** The settings dialog of one project with its own extension states, so the global pages stay untouched. Runs on the UI scope only. */
internal class ProjectSettingsHost(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val notifications: NotificationsState,
    private val skillHost: SkillHost,
    private val reloadGlobalExtensions: () -> Unit,
) {
    var state by mutableStateOf<ProjectSettingsState?>(null)
        private set

    fun open(project: Project, section: ProjectSettingsSection) {
        dispose()
        val dialogScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        state = ProjectSettingsState(
            ExtensionContext.ForProject(project.id, project.name, project.path),
            section,
            dialogScope,
            PluginsState(backend.plugins, dialogScope, requestConfirm, notifications::toast),
            McpState(backend.mcp, project.path, dialogScope, requestConfirm, notifications::toast),
            SkillsState(backend.skills, project.path, dialogScope, requestConfirm, notifications::toast, skillHost),
        )
    }

    fun dispose() {
        state?.dispose()
        state = null
    }

    fun close() {
        val dialog = state ?: return
        if (dialog.busy) {
            notifications.toast(NotificationKind.INFO, "Wait for the running action to finish", "Project settings can be closed afterwards.")
            return
        }
        dispose()
        reloadGlobalExtensions()
    }
}
