package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private fun tooltipOf(tool: ReviewTool): String = "${toolName(tool)} (${toolHotkey(tool)})"

@Composable
fun ReviewToolbar(viewer: ReviewViewerState, tool: ReviewTool, onTool: (ReviewTool) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ReviewToolButtons(tool, onTool)
        Spacer(Modifier.weight(1f))
        IconButton(Icons.ZoomOut, { viewer.zoomOut() }, tooltip = "Zoom out")
        IconButton(Icons.ZoomIn, { viewer.zoomIn() }, tooltip = "Zoom in")
        IconButton(Icons.Maximize, { viewer.fit() }, tooltip = "Fit to view")
        AppButton("100%", { viewer.actualSize() }, variant = ButtonVariant.GHOST, compact = true, tooltip = "Actual size")
    }
}

/** The Pointer/Rectangle/Ellipse/Pin/Freeform buttons with their key hints; call inside a Row. */
@Composable
fun ReviewToolButtons(tool: ReviewTool, onTool: (ReviewTool) -> Unit) {
    ReviewTool.entries.forEach { IconButton(toolIcon(it), { onTool(it) }, tooltip = tooltipOf(it), active = it == tool) }
}
