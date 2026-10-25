package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.choiceLabel
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/**
 * Install control of one catalogue plugin in a context: an "Installed" mark when it is already installed here, otherwise
 * a button that installs straight away or, when the context has several scopes, asks where first.
 */
@Composable
fun InstallButton(context: ExtensionContext, state: PluginsState, pluginId: String, entries: List<InstalledEntry>, modifier: Modifier = Modifier) {
    var choosing by remember { mutableStateOf(false) }
    val busy = state.job?.key == installKey(pluginId)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            entries.any { !it.inherited } -> Chip("Installed", colors.success, leading = { Glyph(Icons.Check, colors.success, size = IconSize.small) })
            else -> {
                if (entries.isNotEmpty()) Label("in user scope", color = colors.textTertiary, style = Type.caption)
                Box {
                    AppButton(
                        "Install",
                        { if (context.writableScopes.size == 1) state.install(pluginId, context.writableScopes.first(), context.projectPath) else choosing = true },
                        icon = Icons.Download,
                        loading = busy,
                        enabled = state.job == null,
                        compact = true,
                    )
                    if (choosing) PopupMenu(context.writableScopes.map { target -> MenuEntry(target.choiceLabel()) { state.install(pluginId, target, context.projectPath) } }, { choosing = false }, alignEnd = true)
                }
            }
        }
    }
}

