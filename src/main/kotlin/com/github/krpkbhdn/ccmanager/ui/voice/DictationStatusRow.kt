package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val IDLE_TEXT = "Hold Space to dictate"
private const val INITIALIZING_TEXT = "Initializing…"
private const val LISTENING_TEXT = "Listening"

/** The line under a dictation field: microphone and what is happening. */
@Composable
fun DictationStatusRow(status: DictationStatus, modifier: Modifier = Modifier) {
    val failed = status as? DictationStatus.Failed
    val tint = if (failed != null) colors.danger else colors.textTertiary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Glyph(Icons.Mic, tint, size = 12.dp)
        Label(statusText(status), color = tint, style = Type.caption, maxLines = 1)
    }
}

private fun statusText(status: DictationStatus) = when (status) {
    DictationStatus.Idle -> IDLE_TEXT
    DictationStatus.Initializing -> INITIALIZING_TEXT
    DictationStatus.Listening -> LISTENING_TEXT
    is DictationStatus.Failed -> status.message
}

