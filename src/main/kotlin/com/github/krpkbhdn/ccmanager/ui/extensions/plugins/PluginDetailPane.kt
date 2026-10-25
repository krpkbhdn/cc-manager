package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginComponent
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.extensions.DetailPaneFrame
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val paneWidth = 380.dp
private const val MAX_NAMES = 24
private const val LIGHT_TOKENS = 1_000
private const val HEAVY_TOKENS = 4_000

/** What the pane shows about one plugin; [description] is used until the details arrive. */
data class PluginDetailTarget(val pluginId: String, val description: String)

/** Detail side pane: identity, caller-supplied [badges] and [actions], then description, token cost and the component inventory. */
@Composable
fun PluginDetailPane(
    target: PluginDetailTarget,
    details: DetailsState?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scroll = rememberScrollState()
    DetailPaneFrame(pluginName(target.pluginId), pluginName(target.pluginId), pluginMarketplace(target.pluginId), onClose, modifier, badges, actions) {
        Box {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                val description = (details as? DetailsState.Loaded)?.details?.description?.takeIf { it.isNotBlank() } ?: target.description
                if (description.isNotBlank()) Label(description, color = colors.textSecondary, maxLines = Int.MAX_VALUE)
                DetailsBody(details)
            }
            ThinScrollbar(scroll)
        }
    }
}

@Composable
private fun DetailsBody(details: DetailsState?) {
    when (details) {
        null, DetailsState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(Radius.lg))
            SkeletonBlock(Modifier.fillMaxWidth().height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(20.dp))
        }
        is DetailsState.Failed -> Label("Components and token cost are not available for this plugin here.", color = colors.textTertiary, style = Type.small, maxLines = 4)
        is DetailsState.Loaded -> {
            TokenCost(details.details.alwaysOnTokens)
            details.details.components.forEach { ComponentGroup(it) }
        }
    }
}

@Composable
private fun TokenCost(tokens: Int) {
    if (tokens <= 0) return
    val tint = costTint(tokens)
    Row(Modifier.fillMaxWidth().card(RoundedCornerShape(Radius.lg), colors.surface).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label("~${compactCount(tokens.toLong())}", color = tint, style = Type.title)
        Column {
            Label("tokens in every session", style = Type.smallMedium)
            Label("Loaded into context even when the plugin is not used", color = colors.textTertiary, style = Type.caption, maxLines = 2)
        }
    }
}

@Composable
private fun costTint(tokens: Int): Color = when {
    tokens < LIGHT_TOKENS -> colors.success
    tokens < HEAVY_TOKENS -> colors.warning
    else -> colors.danger
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentGroup(component: PluginComponent) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(component.kind, style = Type.heading)
            CountBadge(component.count)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            component.names.take(MAX_NAMES).forEach { NameChip(it) }
            if (component.names.size > MAX_NAMES) NameChip("+${component.names.size - MAX_NAMES} more", muted = true)
        }
    }
}

@Composable
private fun NameChip(text: String, muted: Boolean = false) {
    val shape = RoundedCornerShape(Radius.sm)
    Label(text, Modifier.border(1.dp, colors.border, shape).padding(horizontal = Spacing.sm, vertical = Spacing.xxs), color = if (muted) colors.textTertiary else colors.textSecondary, style = Type.monoSmall)
}
