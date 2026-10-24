package com.github.krpkbhdn.ccmanager.ui.state

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Window bounds in dp; a null position lets the OS place the window. */
data class WindowBounds(val width: Int, val height: Int, val x: Int? = null, val y: Int? = null)

/** View preferences that survive restarts. */
data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sidebarCollapsed: Boolean = false,
    val agentsOpen: Boolean = true,
    val quickBarOpen: Boolean = true,
    val window: WindowBounds = WindowBounds(DEFAULT_WIDTH, DEFAULT_HEIGHT),
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
private const val KEY_WIDTH = "window.width"
private const val KEY_HEIGHT = "window.height"
private const val KEY_X = "window.x"
private const val KEY_Y = "window.y"
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
            setProperty(KEY_WIDTH, settings.window.width.toString())
            setProperty(KEY_HEIGHT, settings.window.height.toString())
            settings.window.x?.let { setProperty(KEY_X, it.toString()) }
            settings.window.y?.let { setProperty(KEY_Y, it.toString()) }
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
            window = WindowBounds(
                width = props.getProperty(KEY_WIDTH)?.toIntOrNull() ?: defaults.window.width,
                height = props.getProperty(KEY_HEIGHT)?.toIntOrNull() ?: defaults.window.height,
                x = props.getProperty(KEY_X)?.toIntOrNull(),
                y = props.getProperty(KEY_Y)?.toIntOrNull(),
            ),
        )
    }

    private fun Properties.flag(key: String, default: Boolean): Boolean = getProperty(key)?.toBooleanStrictOrNull() ?: default

    companion object {
        private const val DIRECTORY = ".cc-manager"
        private const val FILE_NAME = "ui-settings.properties"

        fun inUserHome(): UiSettingsStore = UiSettingsStore(Path.of(System.getProperty("user.home"), DIRECTORY, FILE_NAME))
    }
}
