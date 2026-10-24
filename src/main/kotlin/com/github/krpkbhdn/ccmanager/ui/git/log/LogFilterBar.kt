package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.delay

private const val TYPING_DELAY_MILLIS = 350L
private val fieldWidth = 170.dp
private val barHeight = 36.dp
private const val ALL_BRANCHES = "All branches"

/** Branch dropdown, author and text search (applied after a short typing pause), the path filter chip and a reset. */
@Composable
fun LogFilterBar(filter: LogFilter, branches: List<GitBranch>, loading: Boolean, onChange: ((LogFilter) -> LogFilter) -> Unit, modifier: Modifier = Modifier) {
    val author = rememberTextFieldState(filter.author)
    val text = rememberTextFieldState(filter.text)
    val typedAuthor = author.text.toString()
    val typedText = text.text.toString()
    LaunchedEffect(typedAuthor, typedText) {
        if (typedAuthor == filter.author && typedText == filter.text) return@LaunchedEffect
        delay(TYPING_DELAY_MILLIS)
        onChange { it.copy(author = typedAuthor, text = typedText) }
    }
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BranchFilter(filter.branch, branches) { name -> onChange { it.forBranch(name) } }
        AppTextField(author, Modifier.widthIn(max = fieldWidth).weight(1f, fill = false), placeholder = "Author", icon = Icons.User)
        AppTextField(text, Modifier.widthIn(min = fieldWidth).weight(1f), placeholder = "Search message or hash", icon = Icons.Search)
        filter.path?.let { PathChip(it) { onChange { f -> f.forPath(null) } } }
        if (filter.isActive) GhostButton("Reset", { author.edit { replace(0, length, "") }; text.edit { replace(0, length, "") }; onChange { LogFilter() } })
        if (loading) Spinner(size = 14.dp)
    }
}

@Composable
private fun BranchFilter(selected: String?, branches: List<GitBranch>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    Box {
        Pressable({ open = true }, Modifier.height(barHeight).border(1.dp, colors.borderStrong, shape), shape, base = colors.surfaceRaised, hover = colors.surfaceHover) {
            Row(Modifier.padding(horizontal = Spacing.md).height(barHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Glyph(Icons.Branch, colors.accent, size = IconSize.large)
                Label(selected ?: ALL_BRANCHES, Modifier.widthIn(max = fieldWidth), color = colors.textPrimary, style = Type.bodyMedium, tooltipWhenTruncated = true)
                Glyph(Icons.ChevronsUpDown, colors.textTertiary, size = IconSize.medium)
            }
        }
        if (open) {
            val entries = listOf(MenuEntry(ALL_BRANCHES, if (selected == null) Icons.Check else null) { onPick(null) }) +
                branches.filterNot { it.name().endsWith("/HEAD") }.map { MenuEntry(it.name(), if (it.name() == selected) Icons.Check else if (it.remote()) Icons.Globe else null) { onPick(it.name()) } }
            PopupMenu(entries, onDismiss = { open = false })
        }
    }
}

@Composable
private fun PathChip(path: String, onClear: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Row(Modifier.height(barHeight).border(1.dp, colors.borderStrong, shape).padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Glyph(Icons.FileEdit, colors.textTertiary, size = IconSize.medium)
        Label(path, Modifier.widthIn(max = 220.dp), color = colors.textSecondary, style = Type.monoSmall, tooltipWhenTruncated = true)
        IconButton(Icons.Close, onClear, tooltip = "Clear path filter", size = 24.dp)
    }
}
