package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.DEFAULT_MAX_LINES
import com.github.krpkbhdn.ccmanager.ui.components.DEFAULT_MIN_LINES
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

/** Voice settings set once at the window root; null (previews, tests) turns dictation off everywhere. */
val LocalVoice = staticCompositionLocalOf<VoiceSettingsState?> { null }

/** [AppTextArea] with hold-Space dictation and its status row, when voice is configured. Only the focused field gets key events, so several fields in one dialog do not clash. */
@Composable
fun VoiceTextArea(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    minLines: Int = DEFAULT_MIN_LINES,
    maxLines: Int = DEFAULT_MAX_LINES,
    onSubmit: () -> Unit = {},
) {
    val voice = LocalVoice.current
    DictationArea(state, rememberVoiceDictation(state, voice), voice?.dictationUsable == true, modifier, placeholder, focusRequester, minLines, maxLines, onSubmit)
}

@Composable
internal fun DictationArea(
    state: TextFieldState,
    dictation: VoiceDictation?,
    usable: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    minLines: Int = DEFAULT_MIN_LINES,
    maxLines: Int = DEFAULT_MAX_LINES,
    onSubmit: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        AppTextArea(state, modifier, placeholder, focusRequester, minLines = minLines, maxLines = maxLines, onSubmit = onSubmit, voice = dictation)
        if (dictation != null && (usable || dictation.status != DictationStatus.Idle)) DictationStatusRow(dictation.status)
    }
}
