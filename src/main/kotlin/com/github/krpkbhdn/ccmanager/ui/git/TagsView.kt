package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.Tag
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.SHORT_HASH
import com.github.krpkbhdn.ccmanager.ui.git.model.requestDeleteTag
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val rowHeight = 40.dp
private val nameWidth = 200.dp
private val hashWidth = 64.dp

/** Local tags with their commit and message; create at HEAD, delete from the row or its menu. */
@Composable
fun TagsView(git: GitState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Label("${git.tags.size} ${if (git.tags.size == 1) "tag" else "tags"}", Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
            AppButton("Create tag", { git.dialog = GitDialog.NewTag(null) }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true)
        }
        HorizontalRule()
        if (git.tags.isEmpty()) {
            EmptyState(Icons.Tag, "No tags", "Tags mark releases and other points in history worth finding again.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.sm)) {
            items(git.tags, key = { it.name() }) { tag -> TagRow(git, tag) }
        }
    }
}

@Composable
private fun TagRow(git: GitState, tag: Tag) {
    ContextMenuArea({ tagMenu(git, tag) }) {
        Pressable({ git.log.update { it.forBranch(tag.name()) }; git.show(GitSubView.LOG) }, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm)) { hovered ->
            Row(Modifier.height(rowHeight).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Glyph(Icons.Tag, colors.warning, size = IconSize.large)
                Label(tag.name(), Modifier.width(nameWidth), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
                Label(tag.commit().take(SHORT_HASH), Modifier.width(hashWidth), color = colors.textTertiary, style = Type.monoSmall)
                Label(tag.message().orEmpty(), Modifier.weight(1f), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
                if (tag.annotated()) Chip("annotated", colors.textSecondary)
                if (hovered) IconButton(Icons.Trash, { git.requestDeleteTag(tag.name()) }, tooltip = "Delete tag", size = 24.dp, tint = colors.textTertiary)
            }
        }
    }
}

private fun tagMenu(git: GitState, tag: Tag): List<MenuEntry> = listOf(
    MenuEntry("Show in log", Icons.Log) { git.log.update { it.forBranch(tag.name()) }; git.show(GitSubView.LOG) },
    MenuEntry("Copy name", Icons.Copy) { copyToClipboard(tag.name()) },
    MenuEntry("Delete...", Icons.Trash, danger = true, dividerAbove = true) { git.requestDeleteTag(tag.name()) },
)
