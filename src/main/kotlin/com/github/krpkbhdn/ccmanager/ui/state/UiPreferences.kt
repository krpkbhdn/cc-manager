package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The remembered [UiSettings]: held as Compose state, saved off the UI thread, one write at a time. */
class UiPreferences(
    private val store: UiSettingsStore,
    private val scope: CoroutineScope,
    private val sound: NotificationSoundService?,
    private val reportError: (String) -> Unit,
) {
    var settings by mutableStateOf(store.load())
        private set
    private val saveLock = Mutex()

    init {
        sound?.setEnabled(settings.notificationSound)
    }

    fun update(transform: (UiSettings) -> UiSettings) {
        settings = transform(settings)
        val saved = settings
        scope.launch { saveLock.withLock { runGuarded(reportError) { io { store.save(saved) } } } }
        sound?.setEnabled(settings.notificationSound)
    }

    fun toggleSidebar() = update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }

    fun setGroupSessionsByProject(group: Boolean) = update { it.copy(groupSessionsByProject = group) }

    fun toggleNotificationSound() = update { it.copy(notificationSound = !it.notificationSound) }

    fun setAssistantStartup(mode: AssistantStartup) = update { it.copy(assistantStartup = mode) }

    fun cycleAssistantStartup() = update { it.copy(assistantStartup = it.assistantStartup.next()) }

    fun toggleAgents() = update { it.copy(agentsOpen = !it.agentsOpen) }

    fun toggleQuickBar() = update { it.copy(quickBarOpen = !it.quickBarOpen) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    /** Flips between light and dark, starting from whatever [systemDark] says when following the system. */
    fun toggleTheme(systemDark: Boolean) {
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
    }
}
