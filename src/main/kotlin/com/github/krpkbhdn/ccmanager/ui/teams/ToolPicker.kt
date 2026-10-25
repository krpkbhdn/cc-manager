package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val INHERIT_NOTE = "Inherits all tools of the lead. Unticking a tool switches to an allowlist of the ticked built-in tools; add mcp__server__* to keep MCP tools."
private const val CUSTOM_HINT = "Custom: a tool name or mcp__server__*"

/** Tool allowlist as presets, ticked tool chips by group and a field for custom entries such as MCP tools. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolPicker(selection: ToolSelection, onChange: (ToolSelection) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ToolPreset.entries.forEach { preset -> OptionPill(preset.label, selection.activePreset == preset) { onChange(selection.withPreset(preset)) } }
        }
        val note = selection.error ?: if (selection.inheritsAll) INHERIT_NOTE else "${selection.summary} allowed."
        Label(note, color = if (selection.error == null) colors.textTertiary else colors.danger, style = Type.caption, maxLines = 2)
        ToolGroup.entries.forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                Label(group.title, Modifier.padding(top = Spacing.xs + 2.dp).weight(0.22f), color = colors.textSecondary, style = Type.smallMedium)
                FlowRow(Modifier.weight(0.78f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                    group.tools.forEach { tool -> ToolChip(tool, selection.allows(tool)) { onChange(selection.toggle(tool)) } }
                }
            }
        }
        CustomTools(selection, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTools(selection: ToolSelection, onChange: (ToolSelection) -> Unit) {
    val field = remember { TextFieldState() }
    var error by remember { mutableStateOf<String?>(null) }
    val add = {
        runCatching { selection.addCustom(field.text.toString()) }
            .onSuccess { error = null; field.edit { replace(0, length, "") }; onChange(it) }
            .onFailure { error = it.message }
    }
    if (selection.custom.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            selection.custom.forEach { entry -> ToolChip(entry, true, mono = true) { onChange(selection.toggle(entry)) } }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(field, Modifier.weight(1f), placeholder = CUSTOM_HINT, mono = true, onSubmit = { add() })
        AppButton("Add", { add() }, enabled = field.text.isNotBlank())
    }
    error?.let { Label(it, color = colors.danger, style = Type.caption, maxLines = 2) }
}

@Composable
private fun OptionPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    Pressable(onClick, Modifier.border(1.dp, if (selected) colors.accent else colors.border, shape), shape, if (selected) colors.accentSoft else Color.Transparent) {
        Label(text, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 1.dp), color = if (selected) colors.textPrimary else colors.textSecondary, style = if (selected) Type.smallMedium else Type.small)
    }
}

@Composable
private fun ToolChip(tool: String, ticked: Boolean, mono: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    Pressable(onClick, Modifier.border(1.dp, if (ticked) colors.accent.copy(alpha = Alpha.GLOW) else colors.border, shape), shape, if (ticked) colors.accentSoft else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.xs + 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            Glyph(if (ticked) Icons.Check else Icons.Plus, if (ticked) colors.accent else colors.textTertiary, size = 12.dp)
            Label(tool, color = if (ticked) colors.textPrimary else colors.textSecondary, style = if (mono) Type.monoSmall else Type.small)
        }
    }
}
