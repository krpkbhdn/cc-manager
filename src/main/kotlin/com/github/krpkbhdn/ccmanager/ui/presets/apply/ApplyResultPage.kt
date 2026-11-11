package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.apply.ComponentOutcome
import com.github.krpkbhdn.ccmanager.preset.apply.OutcomeStatus
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private fun OutcomeStatus.icon(): ImageVector = when (this) {
    OutcomeStatus.APPLIED -> Icons.CircleCheck
    OutcomeStatus.SKIPPED -> Icons.Minus
    OutcomeStatus.FAILED -> Icons.CircleAlert
    OutcomeStatus.NEEDS_CONFIRMATION -> Icons.Alert
    OutcomeStatus.SIGN_IN_NEEDED -> Icons.LogIn
}

@Composable
private fun OutcomeStatus.tint(): Color = when (this) {
    OutcomeStatus.APPLIED -> colors.success
    OutcomeStatus.SKIPPED -> colors.neutral
    OutcomeStatus.FAILED -> colors.danger
    OutcomeStatus.NEEDS_CONFIRMATION, OutcomeStatus.SIGN_IN_NEEDED -> colors.warning
}

/** Step 3: what happened to each component, with what the user still has to do. */
@Composable
fun ApplyResultPage(state: ApplyPresetState) {
    val result = state.result ?: return
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label(resultLine(result.outcomes()), style = Type.bodyMedium)
            Label(if (result.recorded()) "The preset is recorded as applied to this project." else "Nothing was applied, so the preset is not recorded for this project.", color = colors.textSecondary, style = Type.small, maxLines = 2)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(result.outcomes().size, key = { result.outcomes()[it].componentId() }) { OutcomeRow(result.outcomes()[it]) }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun OutcomeRow(outcome: ComponentOutcome) {
    val shape = RoundedCornerShape(Radius.lg)
    val note = outcomeNote(outcome)
    Row(
        Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Glyph(outcome.status().icon(), outcome.status().tint(), size = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label(outcome.title(), style = Type.bodyMedium, tooltipWhenTruncated = true)
            if (note.isNotBlank()) Label(note, color = colors.textTertiary, style = Type.caption, maxLines = 2, tooltipWhenTruncated = true)
        }
        Chip(statusLabel(outcome.status()), outcome.status().tint())
    }
}
