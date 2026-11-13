package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.InstalledIde
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private const val MAC_OPEN = "open"
private const val MAC_APP_FLAG = "-a"
private const val APP_SUFFIX = ".app"
private const val WINDOWS_OS = "windows"
private const val MAC_OS = "mac"
private val STAR_SIZE = 20.dp
private const val SHIM_ROOT_DEPTH = 4
private const val MAX_SCRIPT_BYTES = 4096
private val SCRIPT_EXECUTABLE = Regex("\"([^\"\\r\\n]+\\.exe)\"", RegexOption.IGNORE_CASE)
private val SHIM_EXECUTABLES = mapOf("code" to "Code.exe", "code-insiders" to "Code - Insiders.exe")

/** The remembered IDE when it is still installed, otherwise the first one found. */
fun resolveDefaultIde(ides: List<InstalledIde>, preferredId: String?): InstalledIde? = ides.firstOrNull { it.id == preferredId } ?: ides.firstOrNull()

/**
 * Files whose system icon stands for the IDE: the .exe on Windows (found behind a .cmd shim by its known layout or by the path the script
 * launches), the .app bundle on macOS. [readScript] gives a script's text, or null when it cannot be read. Nothing else has a real icon.
 */
fun ideIconCandidates(command: List<String>, osName: String, home: Path, readScript: (Path) -> String? = ::readSmallScript): List<Path> {
    val os = osName.lowercase()
    val program = command.firstOrNull() ?: return emptyList()
    return when {
        os.startsWith(WINDOWS_OS) -> windowsCandidates(Path.of(program), readScript)
        os.startsWith(MAC_OS) && program == MAC_OPEN && command.getOrNull(1) == MAC_APP_FLAG -> command.getOrNull(2)?.let { appBundles(it, home) }.orEmpty()
        else -> emptyList()
    }
}

private fun windowsCandidates(program: Path, readScript: (Path) -> String?): List<Path> {
    val name = program.fileName?.toString() ?: return emptyList()
    return when {
        name.endsWith(".exe", ignoreCase = true) -> listOf(program)
        name.endsWith(".cmd", ignoreCase = true) || name.endsWith(".bat", ignoreCase = true) -> launchedExecutable(program, readScript) + siblingExecutables(program, name)
        else -> emptyList()
    }
}

private fun launchedExecutable(script: Path, readScript: (Path) -> String?): List<Path> =
    readScript(script)?.let { SCRIPT_EXECUTABLE.find(it)?.groupValues?.get(1) }?.let { listOf(Path.of(it)) }.orEmpty()

/** VS Code style layouts: `<root>/bin/code.cmd` (or deeper) next to `<root>/Code.exe`. */
private fun siblingExecutables(script: Path, scriptName: String): List<Path> {
    val shim = scriptName.substringBeforeLast('.')
    val exeNames = listOf(SHIM_EXECUTABLES[shim.lowercase()] ?: (shim.replaceFirstChar { it.uppercase() } + ".exe"))
    return generateSequence(script.parent) { it.parent }.take(SHIM_ROOT_DEPTH).flatMap { dir -> exeNames.map(dir::resolve) }.toList()
}

private fun readSmallScript(script: Path): String? = try {
    Files.newInputStream(script).use { String(it.readNBytes(MAX_SCRIPT_BYTES)) }
} catch (e: IOException) {
    null
}

private fun appBundles(application: String, home: Path): List<Path> {
    val bundle = if (application.endsWith(APP_SUFFIX)) application else application + APP_SUFFIX
    return listOf(Path.of("/Applications", bundle), home.resolve("Applications").resolve(bundle))
}

class IdeMenuActions(val open: (String) -> Unit, val makeDefault: (String) -> Unit, val add: () -> Unit, val recheck: () -> Unit)

/** Every IDE as a neutral row (its icon from [glyph], opens on select) with a "default" badge or a make-default star at the end, then adding and re-detecting. */
fun ideMenuEntries(ides: List<InstalledIde>, default: InstalledIde?, actions: IdeMenuActions, glyph: (InstalledIde) -> (@Composable () -> Unit)): List<MenuEntry> {
    val rows = ides.map { ide ->
        val isDefault = ide.id == default?.id
        MenuEntry(
            ide.name,
            leading = glyph(ide),
            trailing = if (isDefault) ({ DefaultBadge() }) else ({ IconButton(Icons.Star, { actions.makeDefault(ide.id) }, tooltip = "Make default", size = STAR_SIZE) }),
            onSelect = { actions.open(ide.id) },
        )
    }
    return rows + listOf(
        MenuEntry("Add IDE...", Icons.Plus, dividerAbove = true, onSelect = actions.add),
        MenuEntry("Check again", Icons.Refresh, onSelect = actions.recheck),
    )
}

@Composable
private fun DefaultBadge() {
    Label(
        "default", Modifier.background(colors.accent.copy(alpha = Alpha.SOFT), RoundedCornerShape(Radius.sm)).padding(horizontal = Spacing.sm, vertical = 1.dp),
        color = colors.accent, style = Type.caption,
    )
}
