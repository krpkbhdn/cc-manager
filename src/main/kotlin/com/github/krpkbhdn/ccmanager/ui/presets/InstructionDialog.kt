package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.FormField
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.MarkdownView
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextField

private val previewMinHeight = 240.dp
private const val BODY_LINES = 12

/** A titled block of markdown that becomes a section of the project's CLAUDE.md, with a preview of how it reads. */
@Composable
fun InstructionDialog(open: ComponentDialog, state: PresetsState) {
    val editing = open.editing
    val initialTitle = editing?.name().orEmpty()
    val initialBody = editing?.payload()?.text("markdown").orEmpty()
    val title = rememberTextFieldState(initialTitle)
    val body = rememberTextFieldState(initialBody)
    var preview by remember { mutableStateOf(false) }
    val canSave = title.text.isNotBlank() && body.text.isNotBlank()
    ComponentDialogFrame(
        if (editing == null) "Add instruction" else "Edit instruction", Icons.Log, if (editing == null) "Add instruction" else "Save", canSave,
        { onError -> state.save(open, PresetComponentKind.INSTRUCTION, title.text.toString(), PresetPayloads.instruction(body.text.toString()), onError) },
        state::closeDialog,
        hasInput = { title.text.toString() != initialTitle || body.text.toString() != initialBody },
    ) {
        FormField("Title", hint = "The heading of the section in CLAUDE.md") {
            VoiceTextField(title, Modifier.fillMaxWidth(), placeholder = "Clean code", height = inputHeight)
        }
        FormField("Instruction", hint = "Markdown. Claude reads it at the start of every session in the project.") {
            SegmentedControl(listOf(Segment(false, "Write"), Segment(true, "Preview")), preview, { preview = it })
            if (preview) {
                Box(Modifier.fillMaxWidth().heightIn(min = previewMinHeight)) { MarkdownView(body.text.toString()) }
            } else {
                VoiceTextArea(body, Modifier.fillMaxWidth(), placeholder = "Rules for Claude, in markdown", minLines = BODY_LINES, maxLines = BODY_LINES)
            }
        }
    }
}
