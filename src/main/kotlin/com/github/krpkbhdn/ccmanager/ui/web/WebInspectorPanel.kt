package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.CopyChip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea

private val propertyWidth = 112.dp
private val styleFieldHeight = 26.dp
private const val COMMENT_MIN_LINES = 2
private const val COMMENT_MAX_LINES = 5
private const val HEX_RGB_DIGITS = 6
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000L
private val swatchSize = 16.dp
private val swatchCorner = 3.dp

/** The picked element (or the hovered one while nothing is picked): box model, editable computed styles and the comment for the item. */
@Composable
fun WebInspectorPanel(view: WebView, state: WebState) {
    val picked = view.picked
    val target = picked ?: view.hovered
    if (target == null) {
        EmptyState(Icons.MousePointer, "No element picked", "Turn on Pick element in the toolbar and click what should change.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        TargetDetails(target, view, Modifier.weight(1f), picked != null) { property, value -> state.editStyle(view, property, value) }
        if (picked != null) {
            HorizontalRule()
            DraftForm(view, onSave = { state.saveDraft(view) }, onCancel = { state.cancelDraft(view) })
        }
    }
}

@Composable
private fun TargetDetails(target: ElementTarget, view: WebView, modifier: Modifier, editable: Boolean, onEdit: (String, String) -> Unit) {
    val list = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            item(key = "header") { TargetHeader(target, view) }
            target.box?.let { box -> item(key = "box") { BoxModelDiagram(box, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) } }
            items((target.computed.keys + view.edits.keys).toList(), key = { "${view.draftGeneration}-$it" }) { property ->
                StyleRow(property, target.computed[property].orEmpty(), view.edits[property], editable) { onEdit(property, it) }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun TargetHeader(target: ElementTarget, view: WebView) {
    Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(formatTargetTitle(target), Modifier.weight(1f), style = Type.monoMedium, tooltipWhenTruncated = true)
            view.editingItem?.let { Chip("Editing #${it.n()}", colors.accent) }
        }
        view.draftPageKey?.takeIf { it != view.pageKey }?.let { Chip("Draft for ${draftPageLabel(view.draftPageUrl)}", colors.warning) }
        if (target.text.isNotBlank()) Label(target.text, color = colors.textSecondary, style = Type.small, maxLines = 2)
        CopyChip(target.selector, stretch = true, label = "Copy selector")
        target.contrast?.let { ContrastRow(it) }
        if (target.stableClasses.isNotEmpty()) SimilarRow(target)
    }
}

@Composable
private fun ContrastRow(contrast: ContrastInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ColorSwatch(contrast.fg)
        ColorSwatch(contrast.bg)
        Label("Contrast ${contrast.ratio}${if (contrast.large) " (large text)" else ""}", Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
        Chip("AA", if (contrast.aa) colors.success else colors.danger)
        Chip("AAA", if (contrast.aaa) colors.success else colors.danger)
    }
}

@Composable
private fun ColorSwatch(hex: String) {
    val shape = RoundedCornerShape(swatchCorner)
    Box(Modifier.size(swatchSize).clip(shape).background(parseHexColor(hex)).border(1.dp, colors.border, shape))
}

@Composable
private fun SimilarRow(target: ElementTarget) {
    val classes = target.stableClasses.joinToString(" ") { ".$it" }
    val count = if (target.similarCount == 0) "No similar elements" else "${target.similarCount} similar"
    Label("$count: $classes", color = colors.textSecondary, style = Type.small, maxLines = 2, tooltipWhenTruncated = true)
}

/** `#rrggbb` from the inspector; anything else paints transparent. */
internal fun parseHexColor(hex: String): Color =
    hex.removePrefix("#").takeIf { it.length == HEX_RGB_DIGITS }?.toLongOrNull(HEX_RADIX)?.let { Color(OPAQUE_ALPHA or it) } ?: Color.Transparent

/** One computed property; the field turns accent-coloured once its value differs from the page's. */
@Composable
private fun StyleRow(property: String, original: String, edited: String?, editable: Boolean, onEdit: (String) -> Unit) = key(property) {
    val shown = edited ?: original
    val changed = edited != null && edited.trim() != original.trim()
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(property, Modifier.width(propertyWidth), color = if (changed) colors.accent else colors.textSecondary, style = Type.monoSmall, tooltipWhenTruncated = true)
        if (editable) {
            val text = rememberTextFieldState(shown)
            val current = rememberUpdatedState(shown)
            LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect { if (it != current.value) onEdit(it) } }
            AppTextField(text, Modifier.weight(1f), mono = true, height = styleFieldHeight)
        } else {
            Label(shown, Modifier.weight(1f), style = Type.monoSmall, tooltipWhenTruncated = true)
        }
    }
}

@Composable
private fun DraftForm(view: WebView, onSave: () -> Unit, onCancel: () -> Unit) {
    val changes = view.styleChanges.size
    Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        VoiceTextArea(view.comment, Modifier.fillMaxWidth(), placeholder = "What should change here?", minLines = COMMENT_MIN_LINES, maxLines = COMMENT_MAX_LINES)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(if (changes == 0) "No style changes" else "$changes style change${if (changes == 1) "" else "s"}", Modifier.weight(1f), color = colors.textTertiary, style = Type.caption)
            AppButton("Cancel", onCancel, variant = ButtonVariant.GHOST, compact = true)
            AppButton(if (view.editingItem == null) "Add to review" else "Update", onSave, variant = ButtonVariant.PRIMARY, enabled = view.canSave && !view.draftSaving, loading = view.draftSaving, compact = true)
        }
    }
}
