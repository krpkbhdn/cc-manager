package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode
import com.github.krpkbhdn.ccmanager.ui.git.model.GitPrefs
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
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
    val lastShell: String? = null,
    val window: WindowBounds = WindowBounds(DEFAULT_WIDTH, DEFAULT_HEIGHT),
    val git: GitPrefs = GitPrefs(),
) {
    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 820
    }
}

private const val KEY_THEME = "theme"
private const val KEY_SIDEBAR = "sidebar.collapsed"
private const val KEY_AGENTS = "agents.open"
private const val KEY_QUICK_BAR = "quickbar.open"
private const val KEY_NOTIFICATION_SOUND = "notification.sound"
private const val KEY_LAST_SHELL = "shell.last"
private const val KEY_WIDTH = "window.width"
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
            settings.lastShell?.let { setProperty(KEY_LAST_SHELL, it) }
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
            lastShell = props.getProperty(KEY_LAST_SHELL),
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

    companion object {
        private const val FILE_NAME = "ui-settings.properties"

        fun inDataDir(dataDir: Path): UiSettingsStore = UiSettingsStore(dataDir.resolve(FILE_NAME))
    }
}
