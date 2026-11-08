package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.usage.history.UsageAlertService
import com.github.krpkbhdn.ccmanager.usage.history.UsageThresholds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** The remembered [UiSettings]: held as Compose state, saved off the UI thread, one write at a time. */
class UiPreferences(
    private val store: UiSettingsStore,
    private val scope: CoroutineScope,
    private val sound: NotificationSoundService?,
    private val usageAlerts: UsageAlertService?,
    private val usageDefaults: UsageThresholds,
    private val reportError: (String) -> Unit,
) {
    var settings by mutableStateOf(store.load())
        private set
    private val saveLock = Mutex()
    private val alertLock = Mutex()
    private var pushedThresholds = usageDefaults

    /** The thresholds in force: the user's value per field, the configured default where none was set. */
    val usageThresholds: UsageThresholds
        get() = UsageThresholds(
            settings.usageFiveHourPercent ?: usageDefaults.fiveHourPercent(),
            settings.usageSevenDayPercent ?: usageDefaults.sevenDayPercent(),
            settings.usageDailyBudgetUsd ?: usageDefaults.dailyBudgetUsd(),
        )

    init {
        applyToBackend()
    }

    fun update(transform: (UiSettings) -> UiSettings) {
        settings = transform(settings)
        val saved = settings
        scope.launch { saveLock.withLock { runGuarded(reportError) { io { store.save(saved) } } } }
        applyToBackend()
    }

    private fun applyToBackend() {
        sound?.setEnabled(settings.notificationSound)
        val thresholds = usageThresholds
        if (thresholds == pushedThresholds) return
        pushedThresholds = thresholds
        usageAlerts?.let { alerts -> scope.launch { alertLock.withLock { runGuarded(reportError) { io { alerts.configure(thresholds) } } } } }
    }

    fun toggleSidebar() = update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }

    fun toggleSectionFolded(section: String) = update { it.copy(foldedSections = it.foldedSections.toggled(section)) }

    fun toggleGroupFolded(groupId: Long) = update { it.copy(foldedGroups = it.foldedGroups.toggled(groupId)) }

    /** Forgets folded groups that no longer exist among the registered [projectIds]. */
    fun retainFoldedGroups(projectIds: Set<Long>) {
        if (settings.foldedGroups.all { it in projectIds }) return
        update { it.copy(foldedGroups = it.foldedGroups.filterTo(HashSet()) { id -> id in projectIds }) }
    }

    fun setGroupSessionsByProject(group: Boolean) = update { it.copy(groupSessionsByProject = group) }

    fun toggleNotificationSound() = update { it.copy(notificationSound = !it.notificationSound) }

    fun toggleOsNotifyNeedsAction() = update { it.copy(osNotifyNeedsAction = !it.osNotifyNeedsAction) }

    fun toggleOsNotifyFinished() = update { it.copy(osNotifyFinished = !it.osNotifyFinished) }

    fun toggleOsNotifyError() = update { it.copy(osNotifyError = !it.osNotifyError) }

    fun toggleMinimizeToTray() = update { it.copy(minimizeToTray = !it.minimizeToTray) }

    fun toggleDoNotDisturb() = update { it.copy(doNotDisturb = !it.doNotDisturb) }

    fun setUsageFiveHourPercent(percent: Int) = update { it.copy(usageFiveHourPercent = percent) }

    fun setUsageSevenDayPercent(percent: Int) = update { it.copy(usageSevenDayPercent = percent) }

    fun setUsageDailyBudgetUsd(budget: Double) = update { it.copy(usageDailyBudgetUsd = budget) }

    fun setAssistantStartup(mode: AssistantStartup) = update { it.copy(assistantStartup = mode) }

    fun cycleAssistantStartup() = update { it.copy(assistantStartup = it.assistantStartup.next()) }

    fun toggleAgents() = update { it.copy(agentsOpen = !it.agentsOpen) }

    fun toggleQuickBar() = update { it.copy(quickBarOpen = !it.quickBarOpen) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setUiScalePercent(percent: Int) = update { it.copy(uiScalePercent = percent.coerceIn(UiSettings.UI_SCALE_RANGE)) }

    fun setFontScalePercent(percent: Int) = update { it.copy(fontScalePercent = percent.coerceIn(UiSettings.FONT_SCALE_RANGE)) }

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
