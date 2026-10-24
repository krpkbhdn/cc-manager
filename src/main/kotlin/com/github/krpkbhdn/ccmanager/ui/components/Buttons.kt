package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/** Wraps [content] in a hover tooltip when [text] is given. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithTooltip(text: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (text == null) return content()
    Tooltip(tooltip = { Text(text) }, modifier = modifier, content = content)
}

@Composable
private fun ButtonContent(text: String, icon: AppIcon?, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Glyph(it, tint, size = 14.dp)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon? = null, enabled: Boolean = true) {
    DefaultButton(onClick = onClick, modifier = modifier, enabled = enabled) { ButtonContent(text, icon, colors.onAccent) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    tooltip: String? = null,
) {
    WithTooltip(tooltip) {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { ButtonContent(text, icon, colors.textSecondary) }
    }
}
