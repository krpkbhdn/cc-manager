package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 520.dp
private val commandMaxHeight = 180.dp

/** Shows the marketplace's command verbatim; nothing runs unless the user confirms. */
@Composable
fun PluginCommandDialog(pending: PendingCommand, confirmEnabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val name = pluginName(pending.pluginId)
    ModalHost(onCancel, width = dialogWidth) {
        DialogBody("Run this command to ${pending.action.name.lowercase()} $name?", Icons.Shield, colors.warning) {
            Label(
                "The ${pluginMarketplace(pending.pluginId)} marketplace wants to run the command below on your computer. Only continue if you trust that marketplace and read the command.",
                color = colors.textSecondary,
                maxLines = 5,
            )
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(colors.terminal).heightIn(max = commandMaxHeight).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                Label(pending.command.command, style = Type.monoBody, maxLines = Int.MAX_VALUE)
            }
            DialogActions("Run command and ${pending.action.name.lowercase()}", confirmEnabled, true, onConfirm, onCancel)
        }
    }
}
