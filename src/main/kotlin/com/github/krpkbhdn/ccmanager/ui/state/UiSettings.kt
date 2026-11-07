package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.git.model.GitPrefs
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.theme.Scale
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Window bounds in dp (of the floating window when [maximized]); a null position lets the OS place the window. */
data class WindowBounds(val width: Int, val height: Int, val x: Int? = null, val y: Int? = null, val maximized: Boolean = false)

/** View preferences that survive restarts. */
data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sidebarCollapsed: Boolean = false,
    val agentsOpen: Boolean = true,
    val quickBarOpen: Boolean = true,
    val notificationSound: Boolean = true,
    val osNotifyNeedsAction: Boolean = true,
    val osNotifyFinished: Boolean = true,
    val osNotifyError: Boolean = true,
    val doNotDisturb: Boolean = false,
    val minimizeToTray: Boolean = true,
    val lastShell: String? = null,
    val groupSessionsByProject: Boolean = false,
    val assistantStartup: AssistantStartup = AssistantStartup.ASK,
    val uiScalePercent: Int = DEFAULT_SCALE_PERCENT,
    val fontScalePercent: Int = DEFAULT_SCALE_PERCENT,
    val voiceEnabled: Boolean = false,
    val voiceModel: String = DEFAULT_VOICE_MODEL,
    val voiceDevice: String = DEFAULT_VOICE_DEVICE,
    val voiceInput: String = "",
    val usageFiveHourPercent: Int? = null,
    val usageSevenDayPercent: Int? = null,
    val usageDailyBudgetUsd: Double? = null,
    val window: WindowBounds = WindowBounds(DEFAULT_WIDTH, DEFAULT_HEIGHT),
    val git: GitPrefs = GitPrefs(),
) {
    val scale: Scale get() = Scale(uiScalePercent / PERCENT, fontScalePercent / PERCENT)

    companion object {
        const val DEFAULT_SCALE_PERCENT = 100
        val UI_SCALE_RANGE = 70..250
        val FONT_SCALE_RANGE = 70..150
        private const val PERCENT = 100f
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 820
        const val DEFAULT_VOICE_MODEL = "base-q5_1"
        const val DEFAULT_VOICE_DEVICE = "best"
    }
}

private const val KEY_THEME = "theme"
private const val KEY_SIDEBAR = "sidebar.collapsed"
private const val KEY_AGENTS = "agents.open"
private const val KEY_QUICK_BAR = "quickbar.open"
private const val KEY_NOTIFICATION_SOUND = "notification.sound"
private const val KEY_OS_NEEDS_ACTION = "notification.os.needsAction"
// Renamed when the default flipped to true: files saved earlier hold an explicit false the user never chose.
private const val KEY_OS_FINISHED = "notification.os.finished.v2"
private const val KEY_OS_ERROR = "notification.os.error"
private const val KEY_DO_NOT_DISTURB = "notification.dnd"
private const val KEY_MINIMIZE_TO_TRAY = "window.minimizeToTray"
private const val KEY_LAST_SHELL = "shell.last"
private const val KEY_GROUP_SESSIONS = "sessions.group"
private const val KEY_ASSISTANT_STARTUP = "assistant.startup"
private const val KEY_UI_SCALE = "scale.ui.percent"
private const val KEY_FONT_SCALE = "scale.font.percent"
private const val KEY_VOICE_ENABLED = "voice.enabled"
private const val KEY_VOICE_MODEL = "voice.model"
private const val KEY_VOICE_DEVICE = "voice.device"
private const val KEY_VOICE_INPUT = "voice.input"
private const val KEY_USAGE_FIVE_HOUR = "usage.fivehour.percent"
private const val KEY_USAGE_SEVEN_DAY = "usage.sevenday.percent"
private const val KEY_USAGE_DAILY_BUDGET = "usage.daily.budget"
private const val KEY_WIDTH ="window.width"
private const val KEY_HEIGHT = "window.height"
private const val KEY_X = "window.x"
private const val KEY_Y = "window.y"
private const val KEY_MAXIMIZED = "window.maximized"
private const val KEY_GIT_VIEW = "git.view."
private const val KEY_PULL_MODE = "git.pull"
private const val KEY_FLAT_CHANGES = "git.changes.flat"
private const val FILE_COMMENT = "Claude Code Manager UI settings"

/** Reads and writes [UiSettings] as a small properties file; a null [file] keeps everything in memory. */
class UiSettingsStore(private val file: Path?) {
    fun load(): UiSettings {
        val source = file?.takeIf { Files.isRegularFile(it) } ?: return UiSettings()
        val props = Properties()
        try {
            Files.newBufferedReader(source).use(props::load)
        } catch (e: IOException) {
            return UiSettings()
        }
        return parse(props)
    }

    fun save(settings: UiSettings) {
        val target = file ?: return
        val props = Properties().apply {
            setProperty(KEY_THEME, settings.themeMode.name)
            setProperty(KEY_SIDEBAR, settings.sidebarCollapsed.toString())
            setProperty(KEY_AGENTS, settings.agentsOpen.toString())
            setProperty(KEY_QUICK_BAR, settings.quickBarOpen.toString())
            setProperty(KEY_NOTIFICATION_SOUND, settings.notificationSound.toString())
            setProperty(KEY_OS_NEEDS_ACTION, settings.osNotifyNeedsAction.toString())
            setProperty(KEY_OS_FINISHED, settings.osNotifyFinished.toString())
            setProperty(KEY_OS_ERROR, settings.osNotifyError.toString())
            setProperty(KEY_DO_NOT_DISTURB, settings.doNotDisturb.toString())
            setProperty(KEY_MINIMIZE_TO_TRAY, settings.minimizeToTray.toString())
            settings.lastShell?.let { setProperty(KEY_LAST_SHELL, it) }
            setProperty(KEY_GROUP_SESSIONS, settings.groupSessionsByProject.toString())
            setProperty(KEY_ASSISTANT_STARTUP, settings.assistantStartup.name)
            setProperty(KEY_UI_SCALE, settings.uiScalePercent.toString())
            setProperty(KEY_FONT_SCALE, settings.fontScalePercent.toString())
            setProperty(KEY_VOICE_ENABLED, settings.voiceEnabled.toString())
            setProperty(KEY_VOICE_MODEL, settings.voiceModel)
            setProperty(KEY_VOICE_DEVICE, settings.voiceDevice)
            setProperty(KEY_VOICE_INPUT, settings.voiceInput)
            settings.usageFiveHourPercent?.let { setProperty(KEY_USAGE_FIVE_HOUR, it.toString()) }
            settings.usageSevenDayPercent?.let { setProperty(KEY_USAGE_SEVEN_DAY, it.toString()) }
            settings.usageDailyBudgetUsd?.let { setProperty(KEY_USAGE_DAILY_BUDGET, it.toString()) }
            setProperty(KEY_WIDTH, settings.window.width.toString())
            setProperty(KEY_HEIGHT, settings.window.height.toString())
            settings.window.x?.let { setProperty(KEY_X, it.toString()) }
            settings.window.y?.let { setProperty(KEY_Y, it.toString()) }
            setProperty(KEY_MAXIMIZED, settings.window.maximized.toString())
            setProperty(KEY_PULL_MODE, settings.git.pullMode.name)
            setProperty(KEY_FLAT_CHANGES, settings.git.flatChanges.toString())
            settings.git.views.forEach { (projectId, view) -> setProperty(KEY_GIT_VIEW + projectId, view.name) }
        }
        try {
            target.parent?.let(Files::createDirectories)
            Files.newBufferedWriter(target).use { props.store(it, FILE_COMMENT) }
        } catch (e: IOException) {
            // Preferences are best effort: losing them must never break the UI.
        }
    }

    private fun parse(props: Properties): UiSettings {
        val defaults = UiSettings()
        return UiSettings(
            themeMode = props.getProperty(KEY_THEME)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            sidebarCollapsed = props.flag(KEY_SIDEBAR, defaults.sidebarCollapsed),
            agentsOpen = props.flag(KEY_AGENTS, defaults.agentsOpen),
            quickBarOpen = props.flag(KEY_QUICK_BAR, defaults.quickBarOpen),
            notificationSound = props.flag(KEY_NOTIFICATION_SOUND, defaults.notificationSound),
            osNotifyNeedsAction = props.flag(KEY_OS_NEEDS_ACTION, defaults.osNotifyNeedsAction),
            osNotifyFinished = props.flag(KEY_OS_FINISHED, defaults.osNotifyFinished),
            osNotifyError = props.flag(KEY_OS_ERROR, defaults.osNotifyError),
            doNotDisturb = props.flag(KEY_DO_NOT_DISTURB, defaults.doNotDisturb),
            minimizeToTray = props.flag(KEY_MINIMIZE_TO_TRAY, defaults.minimizeToTray),
            lastShell = props.getProperty(KEY_LAST_SHELL),
            groupSessionsByProject = props.flag(KEY_GROUP_SESSIONS, defaults.groupSessionsByProject),
            assistantStartup = AssistantStartup.entries.firstOrNull { it.name == props.getProperty(KEY_ASSISTANT_STARTUP) } ?: defaults.assistantStartup,
            uiScalePercent = props.scalePercent(KEY_UI_SCALE, UiSettings.UI_SCALE_RANGE, defaults.uiScalePercent),
            fontScalePercent = props.scalePercent(KEY_FONT_SCALE, UiSettings.FONT_SCALE_RANGE, defaults.fontScalePercent),
            voiceEnabled = props.flag(KEY_VOICE_ENABLED, defaults.voiceEnabled),
            voiceModel = props.getProperty(KEY_VOICE_MODEL) ?: defaults.voiceModel,
            voiceDevice = props.getProperty(KEY_VOICE_DEVICE) ?: defaults.voiceDevice,
            voiceInput = props.getProperty(KEY_VOICE_INPUT) ?: defaults.voiceInput,
            usageFiveHourPercent = props.getProperty(KEY_USAGE_FIVE_HOUR)?.toIntOrNull(),
            usageSevenDayPercent = props.getProperty(KEY_USAGE_SEVEN_DAY)?.toIntOrNull(),
            usageDailyBudgetUsd = props.getProperty(KEY_USAGE_DAILY_BUDGET)?.toDoubleOrNull(),
            window = WindowBounds(
                width = props.getProperty(KEY_WIDTH)?.toIntOrNull() ?: defaults.window.width,
                height = props.getProperty(KEY_HEIGHT)?.toIntOrNull() ?: defaults.window.height,
                x = props.getProperty(KEY_X)?.toIntOrNull(),
                y = props.getProperty(KEY_Y)?.toIntOrNull(),
                maximized = props.flag(KEY_MAXIMIZED, false),
            ),
            git = parseGitPrefs(props),
        )
    }

    private fun parseGitPrefs(props: Properties): GitPrefs {
        val views = props.stringPropertyNames().filter { it.startsWith(KEY_GIT_VIEW) }.mapNotNull { key ->
            val projectId = key.removePrefix(KEY_GIT_VIEW).toLongOrNull()
            val view = GitSubView.entries.firstOrNull { it.name == props.getProperty(key) }
            if (projectId != null && view != null) projectId to view else null
        }.toMap()
        val pull = PullMode.entries.firstOrNull { it.name == props.getProperty(KEY_PULL_MODE) } ?: PullMode.MERGE
        return GitPrefs(views, pull, props.flag(KEY_FLAT_CHANGES, false))
    }

    private fun Properties.flag(key: String, default: Boolean): Boolean = getProperty(key)?.toBooleanStrictOrNull() ?: default

    private fun Properties.scalePercent(key: String, range: IntRange, default: Int): Int =
        getProperty(key)?.toIntOrNull()?.coerceIn(range) ?: default

    companion object {
        private const val FILE_NAME = "ui-settings.properties"

        fun inDataDir(dataDir: Path): UiSettingsStore = UiSettingsStore(dataDir.resolve(FILE_NAME))
    }
}
