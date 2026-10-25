package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.extensions.BusyLabel
import com.github.krpkbhdn.ccmanager.ui.extensions.ListCard
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dotSize = 8.dp
private val transportBox = 36.dp

fun McpStatus.label(): String = when (this) {
    McpStatus.CONNECTED -> "Connected"
    McpStatus.NEEDS_AUTHENTICATION -> "Needs authentication"
    McpStatus.FAILED -> "Failed to connect"
    McpStatus.PENDING -> "Pending"
    McpStatus.UNKNOWN -> "Unknown"
}

@Composable
private fun McpStatus.tint(): Color = when (this) {
    McpStatus.CONNECTED -> colors.success
    McpStatus.NEEDS_AUTHENTICATION -> colors.warning
    McpStatus.FAILED -> colors.danger
    McpStatus.PENDING, McpStatus.UNKNOWN -> colors.textTertiary
}

fun McpApproval.label(): String? = when (this) {
    McpApproval.NOT_REQUIRED -> null
    McpApproval.APPROVED -> "Approved"
    McpApproval.DISABLED -> "Disabled"
    McpApproval.PENDING -> "Needs approval"
}

@Composable
fun McpApproval.tint(): Color = when (this) {
    McpApproval.APPROVED -> colors.success
    McpApproval.PENDING -> colors.warning
    else -> colors.neutral
}

/** Status dot and word of a server: the last health result, a spinner while the first one is pending, or an empty dot. */
@Composable
fun HealthMark(state: McpState, name: String, withText: Boolean = true) {
    val status = state.statusOf(name)?.status
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            status != null -> {
                Box(Modifier.size(dotSize).clip(CircleShape).background(status.tint()))
                if (withText) Label(status.label(), color = colors.textSecondary, style = Type.small)
            }
            state.healthCheck == HealthCheck.RUNNING -> {
                Spinner(size = 12.dp)
                if (withText) Label("Checking", color = colors.textTertiary, style = Type.small)
            }
            else -> Box(Modifier.size(dotSize).clip(CircleShape).background(colors.border))
        }
    }
}

@Composable
fun McpRow(entry: McpEntry, state: McpState) {
    ListCard(selected = state.selected == entry.key, onClick = { state.selected = entry.key }) {
        TransportBadge(entry)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(entry.displayName, style = Type.bodyMedium, tooltipWhenTruncated = true)
                if (entry.fromPlugin) Label(entry.pluginName, color = colors.textTertiary, style = Type.small)
            }
            Label(entry.target.ifBlank { "No target" }, color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
        }
        entry.scope?.let { ScopeBadge(it) } ?: Chip(if (entry.connector) "Connector" else "Plugin", colors.neutral)
        entry.approval.label()?.let { Chip(it, entry.approval.tint()) }
        HealthMark(state, entry.name)
        SignInAction(entry, state)
        if (entry.removable) IconButton(Icons.Trash, { state.requestRemove(entry) }, tooltip = "Remove", tint = colors.danger, enabled = state.job == null)
    }
}

@Composable
fun TransportBadge(entry: McpEntry) {
    val icon = entry.transport?.icon ?: Icons.Plug
    Box(Modifier.size(transportBox).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
        Glyph(icon, colors.accent, size = IconSize.xlarge)
    }
}

/** Compact sign-in affordance beside the status; shown only while the server needs authentication, or while its sign-in runs. */
@Composable
private fun SignInAction(entry: McpEntry, state: McpState) {
    when {
        state.signingIn == entry.name -> BusyLabel("Signing in")
        state.canAuthenticate(entry.name) -> AppButton("Sign in", { state.signIn(entry.name) }, icon = Icons.LogIn, compact = true, tooltip = "Authenticate ${entry.displayName} in your browser")
    }
}
