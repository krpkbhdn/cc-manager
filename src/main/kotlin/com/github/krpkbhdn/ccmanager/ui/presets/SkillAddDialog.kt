package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.picker.FolderPickerDialog
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextField
import java.nio.file.Path

private const val BODY_LINES = 8
private const val SKIPPED_SHOWN = 4

private enum class SkillSource(val label: String) { INSTALLED("Installed skills"), IMPORT("Import folder"), WRITE("Write new") }

/** Adds a skill three ways: copy installed user skills, import a folder of text files, or write a SKILL.md here. */
@Composable
fun SkillAddDialog(open: ComponentDialog, state: PresetsState, dataDir: Path) {
    var source by remember { mutableStateOf(SkillSource.INSTALLED) }
    val chosen = remember { mutableStateListOf<String>() }
    var imported by remember { mutableStateOf<SkillImport?>(null) }
    var picking by remember { mutableStateOf(false) }
    val name = rememberTextFieldState()
    val description = rememberTextFieldState()
    val body = rememberTextFieldState()
    val taken = state.find(open.presetId)?.components().orEmpty().filter { it.kind() == PresetComponentKind.SKILL }.map { it.name() }.toSet()
    val nameError = name.text.toString().takeIf { it.isNotBlank() }?.let { folderNameProblem(it) }
    val canSave = when (source) {
        SkillSource.INSTALLED -> chosen.isNotEmpty()
        SkillSource.IMPORT -> imported?.problem == null && imported != null
        SkillSource.WRITE -> name.text.isNotBlank() && nameError == null && description.text.isNotBlank() && body.text.isNotBlank()
    }
    ComponentDialogFrame(
        "Add skill", Icons.Book, when (source) { SkillSource.INSTALLED -> if (chosen.size > 1) "Add ${chosen.size} skills" else "Add skill"; else -> "Add skill" }, canSave,
        { onError ->
            when (source) {
                SkillSource.INSTALLED -> state.addInstalledSkills(open.presetId, state.skillChoices.orEmpty().filter { it.name() in chosen }, onError)
                SkillSource.IMPORT -> imported?.let { state.save(open, PresetComponentKind.SKILL, it.name, PresetPayloads.skill(it.files), onError) }
                SkillSource.WRITE -> {
                    val text = skillMarkdown(name.text.toString().trim(), description.text.toString(), body.text.toString())
                    state.save(open, PresetComponentKind.SKILL, name.text.toString().trim(), PresetPayloads.skill(mapOf(SKILL_FILE to text)), onError)
                }
            }
        },
        state::closeDialog,
        hasInput = { name.text.isNotEmpty() || description.text.isNotEmpty() || body.text.isNotEmpty() },
    ) {
        SegmentedControl(SkillSource.entries.map { Segment(it, it.label) }, source, { source = it })
        when (source) {
            SkillSource.INSTALLED -> InstalledSkills(state.skillChoices, taken, chosen)
            SkillSource.IMPORT -> ImportFolder(imported, { picking = true })
            SkillSource.WRITE -> WriteSkill(name, nameError, description, body)
        }
    }
    if (picking) FolderPickerDialog(initial = null, dataDir = dataDir, onDismiss = { picking = false }) { folder ->
        picking = false
        imported = null
        state.readFolder(folder) { imported = it }
    }
}

@Composable
private fun InstalledSkills(skills: List<Skill>?, taken: Set<String>, chosen: MutableList<String>) {
    when {
        skills == null -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Label("Loading skills…", color = colors.textSecondary)
        }
        skills.isEmpty() -> Label("No skills of yours were found in ~/.claude/skills. Import a folder or write a new one.", color = colors.textSecondary, maxLines = 3)
        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label("Copies the skill's text files into the preset; later edits to the installed skill do not follow.", color = colors.textTertiary, style = Type.caption, maxLines = 2, modifier = Modifier.padding(bottom = Spacing.xs))
            skills.forEach { skill ->
                val added = skill.name() in taken
                val toggle: () -> Unit = { if (skill.name() in chosen) chosen.remove(skill.name()) else chosen.add(skill.name()) }
                Pressable(toggle, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.md), Color.Transparent, null, enabled = !added) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppCheckbox(added || skill.name() in chosen, { toggle() }, enabled = !added)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Label(skill.name(), style = Type.bodyMedium, color = if (added) colors.textTertiary else colors.textPrimary)
                            if (skill.description().isNotBlank()) Label(skill.description(), color = colors.textTertiary, style = Type.caption)
                        }
                        if (added) Label("Already in the preset", color = colors.textTertiary, style = Type.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFolder(imported: SkillImport?, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label("Pick a skill folder that holds a $SKILL_FILE. Only text files are copied; binary and oversized files are skipped.", color = colors.textTertiary, style = Type.caption, maxLines = 3)
        AppButton(if (imported == null) "Choose folder…" else "Choose another folder…", onChoose, icon = Icons.FolderOpen)
        imported?.let { ImportPreview(it) }
    }
}

@Composable
private fun ImportPreview(imported: SkillImport) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(imported.name, style = Type.bodyMedium)
        Label(imported.summary(), color = colors.textSecondary, style = Type.small)
        imported.problem?.let { Label(it, color = colors.danger, style = Type.small, maxLines = 2) }
        imported.skipped.take(SKIPPED_SHOWN).forEach { Label("Skipped: $it", color = colors.warning, style = Type.caption) }
        if (imported.skipped.size > SKIPPED_SHOWN) Label("and ${imported.skipped.size - SKIPPED_SHOWN} more skipped", color = colors.warning, style = Type.caption)
    }
}

@Composable
private fun WriteSkill(name: TextFieldState, nameError: String?, description: TextFieldState, body: TextFieldState) {
    FormField("Name", error = nameError, hint = "Also the folder name and the /command, for example code-review") {
        AppTextField(name, Modifier.fillMaxWidth(), placeholder = "skill-name", mono = true, height = inputHeight)
    }
    FormField("Description", hint = "Claude reads this to decide when to use the skill") {
        VoiceTextField(description, Modifier.fillMaxWidth(), placeholder = "Reviews a diff for missing tests. Use when the user asks for a review.", height = inputHeight)
    }
    FormField("Instructions", hint = "Markdown. A SKILL.md with front matter is generated from these fields.") {
        VoiceTextArea(body, Modifier.fillMaxWidth(), placeholder = "The steps Claude follows, in markdown", minLines = BODY_LINES, maxLines = BODY_LINES)
    }
}
