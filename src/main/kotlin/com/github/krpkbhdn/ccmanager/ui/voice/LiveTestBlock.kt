package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val TEXT_LINES = 3

/** State line and a fixed-height box showing the tail of what the live test heard; [text] is already shortened and is only selectable when [selectable]. */
@Composable
fun LiveTestBlock(state: LiveTestState, text: String, modifier: Modifier = Modifier, selectable: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LiveTestStatus(state)
        val shape = RoundedCornerShape(Radius.md)
        Column(Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(Spacing.md)) {
            if (selectable) SelectionContainer { Transcript(state, text) } else Transcript(state, text)
        }
    }
}

@Composable
private fun Transcript(state: LiveTestState, text: String) {
    if (text.isEmpty() && state == LiveTestState.Listening) {
        Label("Say something…", color = colors.textTertiary, style = Type.monoBody, minLines = TEXT_LINES, maxLines = TEXT_LINES)
    } else {
        Label(text, style = Type.monoBody, minLines = TEXT_LINES, maxLines = TEXT_LINES)
    }
}

@Composable
private fun LiveTestStatus(state: LiveTestState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (state) {
            LiveTestState.Idle -> Label("Not running", color = colors.textTertiary, style = Type.small)
            LiveTestState.Loading -> {
                Spinner()
                Label("Loading model…", color = colors.textSecondary, style = Type.small)
            }
            LiveTestState.Listening -> {
                StatusDot(AgentStatus.WORKING)
                Label("Listening", color = colors.textSecondary, style = Type.small)
            }
            LiveTestState.Stopped -> {
                Glyph(Icons.MicOff, colors.textTertiary, size = 14.dp)
                Label("Stopped", color = colors.textTertiary, style = Type.small)
            }
            is LiveTestState.Error -> {
                Glyph(Icons.Alert, colors.danger, size = 14.dp)
                Label(state.message, color = colors.danger, style = Type.small, maxLines = TEXT_LINES)
            }
        }
    }
}
