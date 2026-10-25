package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private const val MAX_VISIBLE_CRUMBS = 5
private const val VALIDATION_DEBOUNCE_MILLIS = 80L
private val barHeight = 34.dp
private val crumbMaxWidth = 150.dp
private val suggestionWidth = 380.dp
private val suggestionGapPx = 6

/** Breadcrumb trail that turns into an editable path field with validation and folder suggestions. */
@Composable
fun PathBar(state: FolderPickerState, onFocusList: () -> Unit, modifier: Modifier = Modifier) {
    if (state.editingPath) PathEditor(state, onFocusList, modifier) else Breadcrumbs(state, modifier)
}

@Composable
private fun Breadcrumbs(state: FolderPickerState, modifier: Modifier) {
    val crumbs = FolderRules.collapse(FolderRules.breadcrumbs(state.current), MAX_VISIBLE_CRUMBS)
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier.heightIn(min = barHeight).background(colors.background, shape).border(1.dp, colors.border, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.startEditingPath() }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Icons.Folder, colors.textTertiary, Modifier.padding(horizontal = Spacing.xs))
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) Glyph(Icons.ChevronRight, colors.textTertiary, size = IconSize.small)
            val last = index == crumbs.lastIndex
            Pressable({ crumb.path?.let(state::open) }, enabled = crumb.path != null && !last) {
                Label(
                    crumb.label,
                    Modifier.widthIn(max = crumbMaxWidth).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    color = if (last) colors.textPrimary else colors.textSecondary,
                    style = if (last) Type.heading else Type.body,
                )
            }
        }
    }
}

@Composable
private fun PathEditor(state: FolderPickerState, onFocusList: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var check by remember { mutableStateOf(PathCheck.Valid) }
    var suggestions by remember { mutableStateOf(emptyList<Path>()) }
    var suggestionsFor by remember { mutableStateOf("") }
    val text = state.pathText.text.toString()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        delay(VALIDATION_DEBOUNCE_MILLIS)
        val result = withContext(Dispatchers.IO) { FolderRules.checkText(text) to FolderRules.suggestions(text) }
        check = result.first
        suggestions = result.second
        suggestionsFor = text
    }
    fun finish() {
        state.stopEditingPath()
        onFocusList()
    }
    fun acceptSuggestion(path: Path) = state.startEditingPath(path.toString() + path.fileSystem.separator)
    Box(modifier) {
        PickerField(
            state.pathText, "Type or paste a folder path", Modifier.fillMaxWidth(), focus, invalid = check != PathCheck.Valid && text.isNotBlank(), mono = true,
            leading = { Glyph(Icons.Folder, colors.textTertiary) },
            trailing = { if (text.isNotBlank()) check.message?.let { Label(it, color = colors.danger, style = Type.small) } },
        ) { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.Enter -> true.also { scope.launch { if (state.commitTypedPath() == PathCheck.Valid) finish() } }
                event.key == Key.Escape -> true.also { finish() }
                event.key == Key.Tab && suggestions.isNotEmpty() && suggestionsFor == text -> true.also { acceptSuggestion(suggestions.first()) }
                else -> false
            }
        }
        if (suggestions.isNotEmpty()) SuggestionPopup(suggestions, ::acceptSuggestion)
    }
}

@Composable
private fun SuggestionPopup(suggestions: List<Path>, onPick: (Path) -> Unit) {
    val offsetY = with(LocalDensity.current) { barHeight.roundToPx() + suggestionGapPx }
    Popup(alignment = Alignment.TopStart, offset = IntOffset(0, offsetY), properties = PopupProperties(focusable = false)) {
        Column(Modifier.widthIn(max = suggestionWidth).card(RoundedCornerShape(Radius.md), colors.surfaceRaised, raised = true).padding(Spacing.xs), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            suggestions.forEach { path ->
                Pressable({ onPick(path) }, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Glyph(Icons.Folder, colors.accent)
                        Label(path.fileName?.toString() ?: path.toString())
                    }
                }
            }
            Label("Tab completes the first match", Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), color = colors.textTertiary, style = Type.caption)
        }
    }
}
