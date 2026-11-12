package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import androidx.compose.foundation.shape.RoundedCornerShape

private val menuMinWidth = 240.dp

/** "div#app.card" for a layer row. */
internal fun layerLabel(layer: LiteTarget): String =
    layer.tag + (if (layer.id.isEmpty()) "" else "#${layer.id}") + layer.stableClasses.joinToString("") { ".$it" }

/** The page's right-click menu at the click spot: actions on the chosen layer and the list of layers under the point. */
@Composable
fun WebContextMenu(view: WebView, state: WebState) {
    val menu = view.contextMenu?.takeIf { !it.feedback && !it.quick } ?: return
    val at = IntOffset(menu.canvasOffset.x.toInt(), menu.canvasOffset.y.toInt())
    val close = { WebContextActions.dismiss(view) }
    val layer = menu.items.getOrNull(menu.chosen)
    fun inspect(reason: String) = layer?.let { state.requestInspect(view, it.selector, reason) }
    val entries = listOf(
        MenuEntry("Leave feedback", Icons.Message, hint = "Q", enabled = layer != null, onSelect = { inspect(REASON_FEEDBACK) }),
        MenuEntry("Edit styles", Icons.Edit, enabled = layer != null, onSelect = { inspect(REASON_STYLES) }),
        MenuEntry("Select similar", Icons.Layers, enabled = layer != null, onSelect = { view.host.runScript(InspectorBridge.highlight("")); view.host.runScript(InspectorBridge.showSimilar(layer!!.selector)); view.contextMenu = null }),
        MenuEntry("Copy selector", Icons.Copy, enabled = layer != null, onSelect = { copyToClipboard(layer!!.selector); close() }),
    )
    AnchoredPopup(close, modifier = Modifier.width(menuMinWidth), at = at) {
        Column {
            menu.notice?.let { Label(it, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), color = colors.warning, style = Type.caption) }
            MenuColumn(entries) {}
            if (menu.items.isNotEmpty()) {
                HorizontalRule(Modifier.padding(vertical = Spacing.xs))
                Label("Layers", Modifier.padding(horizontal = Spacing.md), color = colors.textTertiary, style = Type.caption)
                Column(Modifier.padding(Spacing.xs), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    menu.items.forEachIndexed { i, item -> LayerRow(view, menu, i, item) }
                }
            }
        }
    }
}

@Composable
private fun LayerRow(view: WebView, menu: ContextMenuState, index: Int, item: LiteTarget) {
    val chosen = index == menu.chosen
    Pressable(
        { view.contextMenu = menu.copy(chosen = index) },
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        base = if (chosen) colors.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
        hover = colors.surfaceHover,
    ) { hovered ->
        LaunchedEffect(hovered, chosen) { if (hovered) view.host.runScript(InspectorBridge.highlight(item.selector)) }
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Label(layerLabel(item), Modifier.weight(1f), style = Type.monoSmall, tooltipWhenTruncated = true)
        }
    }
}
