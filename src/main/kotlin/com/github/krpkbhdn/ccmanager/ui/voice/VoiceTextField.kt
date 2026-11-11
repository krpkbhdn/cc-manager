package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DEFAULT_FIELD_HEIGHT
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

/** [AppTextField] with hold-Space dictation and its status row, when voice is configured; line breaks in dictated text become spaces. */
@Composable
fun VoiceTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
    height: Dp = DEFAULT_FIELD_HEIGHT,
) {
    val voice = LocalVoice.current
    DictationField(state, rememberVoiceDictation(state, voice, singleLine = true), voice?.dictationUsable == true, modifier, placeholder, focusRequester, onSubmit, height)
}

@Composable
internal fun DictationField(
    state: TextFieldState,
    dictation: VoiceDictation?,
    usable: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
    height: Dp = DEFAULT_FIELD_HEIGHT,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        AppTextField(state, modifier, placeholder, focusRequester = focusRequester, onSubmit = onSubmit, voice = dictation, height = height)
        if (dictation != null && (usable || dictation.status != DictationStatus.Idle)) DictationStatusRow(dictation.status)
    }
}
