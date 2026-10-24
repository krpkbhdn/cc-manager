package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.foundation.theme.JewelTheme

private val fieldHeight = 34.dp
private val fieldBorder = 1.dp

/** Single-line text input drawn with theme tokens; [onKey] runs before the field sees the key. */
@Composable
fun PickerField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    invalid: Boolean = false,
    mono: Boolean = false,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    onKey: (KeyEvent) -> Boolean = { false },
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val outline = when {
        invalid -> colors.danger
        focused -> colors.accent
        else -> colors.border
    }
    val shape = RoundedCornerShape(Radius.md)
    val style = JewelTheme.defaultTextStyle.copy(
        color = colors.textPrimary,
        fontSize = FontSize.body,
        fontFamily = if (mono) FontFamily.Monospace else JewelTheme.defaultTextStyle.fontFamily,
    )
    BasicTextField(
        state = state,
        modifier = modifier.let { base -> focusRequester?.let { base.focusRequester(it) } ?: base }.onPreviewKeyEvent(onKey),
        textStyle = style,
        cursorBrush = SolidColor(colors.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = source,
        decorator = { inner ->
            Row(
                Modifier.heightIn(min = fieldHeight).background(colors.background, shape)
                    .border(fieldBorder, outline, shape).padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                leading()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Label(placeholder, color = colors.textTertiary)
                    inner()
                }
                trailing()
            }
        },
    )
}
