package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.Banner
import com.github.krpkbhdn.ccmanager.ui.components.ConfirmDialog
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.RenameDialog
import com.github.krpkbhdn.ccmanager.ui.components.ToastHost
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val SIGNED_OUT_MESSAGE = "Claude is signed out. Run `claude` once in a terminal and sign in."

@Composable
fun AppRoot(state: AppState, osName: String = System.getProperty("os.name")) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
        val installation = state.installation
        when {
            installation == null -> Unit
            !installation.installed -> InstallGate(installation.problem, state.checkingInstallation, { state.recheckInstallation() }, osName)
            else -> MainLayout(state, installation)
        }
        ToastHost(state.toasts, state::openToast, state::dismissToast, Modifier.align(Alignment.BottomEnd))
        state.confirm?.let { ConfirmDialog(it) { state.confirm = null } }
        state.renaming?.let { RenameDialog(it, { name -> state.renameProject(it.project, name) }, { state.renaming = null }) }
    }
}

@Composable
private fun MainLayout(state: AppState, installation: ClaudeInstallation) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(state)
            Column(Modifier.weight(1f).fillMaxSize()) {
                TopBar(routeCrumbs(state), state.unreadCount, { state.notificationsOpen = !state.notificationsOpen })
                if (installation.login == LoginState.LOGGED_OUT && !state.loginBannerDismissed) {
                    Banner(SIGNED_OUT_MESSAGE, colors.warning, AppIcon.Alert) { IconAction(AppIcon.Close, { state.loginBannerDismissed = true }) }
                }
                RouteContent(state)
            }
        }
        if (state.notificationsOpen) NotificationPanel(state)
    }
}

private fun routeCrumbs(state: AppState): List<String> = when (val route = state.route) {
    Route.Overview -> listOf("Overview")
    is Route.Project -> listOf("Projects", state.projectName(route.id))
}

@Composable
private fun RouteContent(state: AppState) {
    val route = state.route
    val project = (route as? Route.Project)?.let { target -> state.projects.firstOrNull { it.id == target.id } }
    if (project != null) ProjectScreen(state, project) else OverviewScreen(state)
}
