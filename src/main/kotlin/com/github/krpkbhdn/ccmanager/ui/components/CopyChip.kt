package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay

private const val CONFIRM_MILLIS = 1_400L

fun copyToClipboard(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

/**
 * Monospace chip showing [text]; clicking copies [copyText] and flips the icon to a check for a moment.
 * With [stretch] the chip fills the available width and the icon sits at its end.
 */
@Composable
fun CopyChip(
    text: String,
    modifier: Modifier = Modifier,
    copyText: String = text,
    style: TextStyle = Type.monoSmall,
    label: String = "Copy",
    stretch: Boolean = false,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(CONFIRM_MILLIS)
            copied = false
        }
    }
    val shape = RoundedCornerShape(Radius.sm)
    WithTooltip(if (copied) "Copied" else label) {
        Pressable({ copyToClipboard(copyText); copied = true }, modifier.then(if (stretch) Modifier.fillMaxWidth() else Modifier).border(1.dp, colors.border, shape), shape) {
            Row(
                Modifier.then(if (stretch) Modifier.fillMaxWidth() else Modifier).padding(start = Spacing.sm + 2.dp, end = Spacing.sm - 1.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 1.dp),
            ) {
                Label(text, Modifier.weight(1f, fill = stretch), color = colors.textSecondary, style = style)
                Glyph(if (copied) Icons.Check else Icons.Copy, if (copied) colors.success else colors.textTertiary, size = IconSize.small)
            }
        }
    }
}
