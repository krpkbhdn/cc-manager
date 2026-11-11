package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.runtime.Composable
import java.nio.file.Path

/** Shows the dialog the user opened on the Project presets page; [dataDir] is where the folder picker keeps its recent folders. */
@Composable
fun PresetDialogHost(open: ComponentDialog, state: PresetsState, dataDir: Path) {
    when (open.editor) {
        ComponentEditor.PLUGINS -> PluginPickerDialog(open, state)
        ComponentEditor.SKILL_ADD -> SkillAddDialog(open, state, dataDir)
        ComponentEditor.SKILL_EDIT -> SkillEditDialog(open, state)
        ComponentEditor.INSTRUCTION -> InstructionDialog(open, state)
        ComponentEditor.MCP -> McpPresetDialog(open, state)
        ComponentEditor.AGENT -> AgentDialog(open, state)
        ComponentEditor.FILE -> FileDialog(open, state)
        ComponentEditor.SETTINGS -> SettingsFragmentDialog(open, state)
    }
}
