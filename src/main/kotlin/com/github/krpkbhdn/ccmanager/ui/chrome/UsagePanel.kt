package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.SessionUsageRow
import com.github.krpkbhdn.ccmanager.ui.state.UsageWindowDetail
import com.github.krpkbhdn.ccmanager.ui.state.sessionUsageRows
import com.github.krpkbhdn.ccmanager.ui.state.usageFreshness
import com.github.krpkbhdn.ccmanager.ui.state.usageWindowDetails
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val panelWidth = 380.dp
private val progressHeight = 6.dp
private const val NO_LIMITS_NOTE = "Usage limits are reported by Claude Code for Pro and Max plans only. Other plans, such as Team, show the per-session figures below."
private const val NO_REPLY_NOTE = "Limits appear after a session of a Pro or Max plan gets its first reply."

/** Detail panel of the usage chip: limit windows with exact numbers, or an explanation when Claude reports none, and the per-session figures. */
@Composable
internal fun UsagePanel(state: AppState, onDismiss: () -> Unit) {
    val windows = usageWindowDetails(state.rateLimits, state.now)
    val rows = sessionUsageRows(state.live.sessions, state.sessionUsage, state.projects::nameOf)
    AnchoredPopup(onDismiss, alignEnd = true, modifier = Modifier.width(panelWidth)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Usage", Modifier.weight(1f), style = Type.heading)
                state.rateLimits?.takeIf { windows.isNotEmpty() }?.let { Label(usageFreshness(it, state.now), color = colors.textTertiary, style = Type.caption) }
            }
            if (windows.isEmpty()) Label(if (state.rateLimits == null) NO_LIMITS_NOTE else NO_REPLY_NOTE, color = colors.textSecondary, style = Type.small, maxLines = 4)
            windows.forEach { WindowDetail(it) }
            HorizontalRule()
            Label("Sessions", color = colors.textSecondary, style = Type.smallMedium)
            if (rows.isEmpty()) Label("No running sessions", color = colors.textTertiary, style = Type.small)
            rows.forEach { SessionRow(it) }
        }
    }
}

@Composable
private fun WindowDetail(detail: UsageWindowDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(detail.title, Modifier.weight(1f), style = Type.bodyMedium)
            Label(detail.usedText, color = levelColor(detail.level), style = Type.numeric)
            Label("  ·  " + detail.remainingText, color = colors.textSecondary, style = Type.numeric)
        }
        Box(Modifier.fillMaxWidth().height(progressHeight).background(colors.border, RoundedCornerShape(Radius.pill))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(detail.fraction).background(levelColor(detail.level), RoundedCornerShape(Radius.pill)))
        }
        Label("${detail.resetsInText}  ·  ${detail.resetsAtText}", color = colors.textTertiary, style = Type.caption)
    }
}

@Composable
private fun SessionRow(row: SessionUsageRow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
        StatusDot(row.status, size = 8.dp)
        Column(Modifier.weight(1f)) {
            Label(row.title, style = Type.small, tooltipWhenTruncated = true)
            Label(listOfNotNull(row.projectName, row.model, row.effort).joinToString("  ·  "), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
        }
        Column(horizontalAlignment = Alignment.End) {
            Label(row.contextPercent?.let { "$it% context" } ?: "-", color = colors.textSecondary, style = Type.numericCaption)
            Label(row.costText ?: "-", color = colors.textTertiary, style = Type.numericCaption)
        }
    }
}
