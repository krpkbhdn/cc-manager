package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val MIN_LINES = 2
private const val MAX_LINES = 8

/** Multi-line field with the app's focus ring; Ctrl/Cmd+Enter calls [onSubmit]. */
@Composable
fun AppTextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    mono: Boolean = false,
    onSubmit: () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) colors.accent else colors.borderStrong, Motion.fast(), label = "areaBorder")
    val shape = RoundedCornerShape(Radius.md)
    val style = (if (mono) Type.monoBody else Type.body).copy(color = colors.textPrimary)
    BasicTextField(
        state = state,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                val submit = event.type == KeyEventType.KeyDown && isConfirmKey(event.key) && (event.isCtrlPressed || event.isMetaPressed)
                if (submit) onSubmit()
                submit
            },
        textStyle = style,
        cursorBrush = SolidColor(colors.accent),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = MIN_LINES, maxHeightInLines = MAX_LINES),
        interactionSource = source,
        decorator = { inner ->
            Box(Modifier.background(colors.background, shape).border(1.dp, borderColor, shape).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                if (state.text.isEmpty()) Label(placeholder, color = colors.textTertiary, style = style, maxLines = 1)
                inner()
            }
        },
    )
}
