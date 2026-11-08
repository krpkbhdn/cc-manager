package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private const val SETTINGS_LINES = 14
private const val EXAMPLE = "{\n  \"permissions\": {\n    \"deny\": [\"Read(./.env)\"]\n  }\n}"

/** The one settings fragment of a preset, merged into the project's .claude/settings.json; it must be a JSON object. */
@Composable
fun SettingsFragmentDialog(open: ComponentDialog, state: PresetsState) {
    val editing = open.editing
    val initial = editing?.payload()?.path("fragment")?.let(::prettyJson).orEmpty()
    val json = rememberTextFieldState(initial)
    val problem = json.text.toString().takeIf { it.isNotBlank() }?.let { settingsProblem(it) }
    ComponentDialogFrame(
        if (editing == null) "Add settings" else "Edit settings", Icons.Settings, if (editing == null) "Add settings" else "Save",
        json.text.isNotBlank() && problem == null,
        { onError -> state.save(open, PresetComponentKind.SETTINGS, SETTINGS_NAME, PresetPayloads.settings(parseSettings(json.text.toString())), onError) },
        state::closeDialog,
        hasInput = { json.text.toString() != initial },
    ) {
        FormField("Settings", error = problem, hint = "A JSON object that is merged into the project's .claude/settings.json") {
            AppTextArea(json, Modifier.fillMaxWidth(), placeholder = EXAMPLE, mono = true, minLines = SETTINGS_LINES, maxLines = SETTINGS_LINES)
        }
    }
}
