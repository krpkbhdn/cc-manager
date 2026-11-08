package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private const val SKILL_LINES = 14

/** Edits the SKILL.md of a skill in the preset; its other files are kept as they are. */
@Composable
fun SkillEditDialog(open: ComponentDialog, state: PresetsState) {
    val skill = open.editing ?: return
    val files = skill.payload().skillFiles()
    val initialName = skill.name()
    val initialText = files[SKILL_FILE].orEmpty()
    val name = rememberTextFieldState(initialName)
    val text = rememberTextFieldState(initialText)
    val nameError = name.text.toString().takeIf { it.isNotBlank() }?.let { folderNameProblem(it) }
    val others = files.size - 1
    ComponentDialogFrame(
        "Edit skill", Icons.Book, "Save", name.text.isNotBlank() && nameError == null && text.text.isNotBlank(),
        { onError -> state.save(open, PresetComponentKind.SKILL, name.text.toString(), PresetPayloads.skill(files + (SKILL_FILE to text.text.toString())), onError) },
        state::closeDialog,
        hasInput = { name.text.toString() != initialName || text.text.toString() != initialText },
    ) {
        FormField("Name", error = nameError, hint = "Also the folder name: .claude/skills/<name>") {
            AppTextField(name, Modifier.fillMaxWidth(), mono = true, height = inputHeight)
        }
        FormField(SKILL_FILE, hint = if (others > 0) "$others more ${if (others == 1) "file" else "files"} in the skill folder stay unchanged" else null) {
            AppTextArea(text, Modifier.fillMaxWidth(), mono = true, minLines = SKILL_LINES, maxLines = SKILL_LINES)
        }
    }
}
