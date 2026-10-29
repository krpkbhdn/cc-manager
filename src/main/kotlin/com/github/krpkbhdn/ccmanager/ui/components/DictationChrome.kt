package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.CARET_GAP_IN_SPACES
import com.github.krpkbhdn.ccmanager.ui.voice.CaretMotion
import com.github.krpkbhdn.ccmanager.ui.voice.DictationStatus
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceDictation
import com.github.krpkbhdn.ccmanager.ui.voice.dictationCaret
import com.github.krpkbhdn.ccmanager.ui.voice.rememberCaretMotion

/** What AppTextArea and AppTextField share for dictation: key handling, hidden native caret with the animated overlay, placeholder rule. */
internal class DictationChrome(
    private val voice: VoiceDictation?,
    val listening: Boolean,
    val dictating: Boolean,
    private val motion: CaretMotion?,
    private val gap: Float,
    private val layout: () -> TextLayoutResult?,
    val onTextLayout: Density.(() -> TextLayoutResult?) -> Unit,
) {
    val cursorColor: Color @Composable get() = if (listening) Color.Transparent else colors.accent



    fun keys(extra: (KeyEvent) -> Boolean = { false }): Modifier = Modifier.onPreviewKeyEvent { event -> extra(event) or (voice?.onKeyEvent(event) == true) }

    @Composable
    fun caret(state: TextFieldState, scroll: ScrollState, horizontal: Boolean): Modifier =
        if (motion != null) Modifier.dictationCaret(state, layout, scroll, motion, colors.textSecondary, gap, horizontal) else Modifier

    fun showPlaceholder(state: TextFieldState) = state.text.isEmpty() && !dictating
}

@Composable
internal fun rememberDictationChrome(voice: VoiceDictation?, style: TextStyle, focused: Boolean): DictationChrome {
    var layout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val listening = voice?.status == DictationStatus.Listening
    val dictating = listening || voice?.status == DictationStatus.Initializing
    val measurer = rememberTextMeasurer()
    val gap = remember(style) { CARET_GAP_IN_SPACES * (measurer.measure("a a", style).size.width - measurer.measure("aa", style).size.width) }
    val motion = if (listening) rememberCaretMotion(voice?.level ?: 0f) else null
    LaunchedEffect(focused) { if (!focused) voice?.lostFocus() }
    return DictationChrome(voice, listening, dictating, motion, gap, { layout?.invoke() }) { layout = it }
}
