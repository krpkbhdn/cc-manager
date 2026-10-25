package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.extensions.MonoBlock
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val markerWidth = 18.dp

/** Readable rendering of a SKILL.md body: headings, lists and code blocks; no inline formatting. */
@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { markdownBlocks(markdown) }
    SelectionContainer {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Label(block.text, Modifier.padding(top = if (block.level <= 2) Spacing.sm else 0.dp), style = if (block.level == 1) Type.subtitle else Type.heading, maxLines = Int.MAX_VALUE)
                    is MarkdownBlock.Paragraph -> Label(block.text, color = colors.textSecondary, maxLines = Int.MAX_VALUE)
                    is MarkdownBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Label(block.marker, Modifier.widthIn(min = markerWidth), color = colors.textTertiary)
                        Label(block.text, color = colors.textSecondary, maxLines = Int.MAX_VALUE)
                    }
                    is MarkdownBlock.Code -> MonoBlock(block.text)
                }
            }
        }
    }
}
