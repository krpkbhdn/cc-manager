package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val fieldHeight = 36.dp
private const val MASK_CHAR = '•'

/** Single-line text field with an optional leading icon and placeholder, a focus ring and Enter-to-submit. */
@Composable
fun AppTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    mono: Boolean = false,
    masked: Boolean = false,
    onSubmit: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) colors.accent else colors.borderStrong, Motion.fast(), label = "fieldBorder")
    val shape = RoundedCornerShape(Radius.md)
    val style = (if (mono) Type.monoBody else Type.body).copy(color = colors.textPrimary)
    BasicTextField(
        state = state,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = style,
        cursorBrush = SolidColor(colors.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation { },
        outputTransformation = if (masked) OutputTransformation { replace(0, length, MASK_CHAR.toString().repeat(length)) } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = KeyboardActionHandler { onSubmit() },
        interactionSource = source,
        decorator = { inner ->
            Row(
                Modifier.fillMaxWidth().height(fieldHeight).background(colors.background, shape).border(1.dp, borderColor, shape).padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                icon?.let { Glyph(it, if (focused) colors.accent else colors.textTertiary) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Label(placeholder, color = colors.textTertiary, style = style)
                    inner()
                }
                trailing()
            }
        },
    )
}
