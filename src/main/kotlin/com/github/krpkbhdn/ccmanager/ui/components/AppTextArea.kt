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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.krpkbhdn.ccmanager.ui.voice.DictationStatus
import com.github.krpkbhdn.ccmanager.ui.voice.CARET_GAP_IN_SPACES
import com.github.krpkbhdn.ccmanager.ui.voice.dictationCaret
import com.github.krpkbhdn.ccmanager.ui.voice.rememberCaretMotion
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
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceDictation

private const val DEFAULT_MIN_LINES = 2
private const val DEFAULT_MAX_LINES = 8

/** Multi-line field with the app's focus ring; Ctrl/Cmd+Enter calls [onSubmit]; with [voice], holding Space dictates. */
@Composable
fun AppTextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    mono: Boolean = false,
    minLines: Int = DEFAULT_MIN_LINES,
    maxLines: Int = DEFAULT_MAX_LINES,
    onSubmit: () -> Unit = {},
    voice: VoiceDictation? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) colors.accent else colors.borderStrong, Motion.fast(), label = "areaBorder")
    val shape = RoundedCornerShape(Radius.md)
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val listening = voice?.status == DictationStatus.Listening
    val dictating = listening || voice?.status == DictationStatus.Initializing
    val style = (if (mono) Type.monoBody else Type.body).copy(color = colors.textPrimary)
    val measurer = rememberTextMeasurer()
    val gap = remember(style) { CARET_GAP_IN_SPACES * (measurer.measure("a a", style).size.width - measurer.measure("aa", style).size.width) }
    val motion = if (listening) rememberCaretMotion(voice?.level ?: 0f) else null
    LaunchedEffect(focused) { if (!focused) voice?.lostFocus() }
    BasicTextField(
        state = state,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                val submit = event.type == KeyEventType.KeyDown && isConfirmKey(event.key) && (event.isCtrlPressed || event.isMetaPressed)
                if (submit) onSubmit()
                val dictating = voice?.onKeyEvent(event) == true
                submit || dictating
            },
        textStyle = style,
        cursorBrush = SolidColor(if (listening) Color.Transparent else colors.accent),
        scrollState = scroll,
        onTextLayout = { layout = it },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines),
        interactionSource = source,
        outputTransformation = voice?.outputTransformation(colors.textSecondary),
        decorator = { inner ->
            Box(Modifier.background(colors.background, shape).border(1.dp, borderColor, shape).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                if (state.text.isEmpty() && !dictating) Label(placeholder, color = colors.textTertiary, style = style, maxLines = 1)
                Box(if (motion != null) Modifier.dictationCaret(state, { layout?.invoke() }, scroll, motion, colors.textSecondary, gap) else Modifier) { inner() }
            }
        },
    )
}
