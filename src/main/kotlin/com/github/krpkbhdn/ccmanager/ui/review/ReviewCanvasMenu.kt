package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

internal fun toolName(tool: ReviewTool): String = when (tool) {
    ReviewTool.POINTER -> "Pointer"
    ReviewTool.RECTANGLE -> "Rectangle"
    ReviewTool.ELLIPSE -> "Ellipse"
    ReviewTool.PIN -> "Pin"
    ReviewTool.FREEFORM -> "Freeform"
}

internal fun toolHotkey(tool: ReviewTool): String = when (tool) {
    ReviewTool.POINTER -> "V"
    ReviewTool.RECTANGLE -> "R"
    ReviewTool.ELLIPSE -> "E"
    ReviewTool.PIN -> "P"
    ReviewTool.FREEFORM -> "F"
}

internal fun toolIcon(tool: ReviewTool): ImageVector = when (tool) {
    ReviewTool.POINTER -> Icons.MousePointer
    ReviewTool.RECTANGLE -> Icons.Square
    ReviewTool.ELLIPSE -> Icons.Circle
    ReviewTool.PIN -> Icons.MapPin
    ReviewTool.FREEFORM -> Icons.PenTool
}

private val drawingTools = listOf(ReviewTool.RECTANGLE, ReviewTool.ELLIPSE, ReviewTool.PIN, ReviewTool.FREEFORM)

/** Right-click menu: arms one-shot drawing of one element; over a mark it also offers to select or delete it. */
internal fun canvasMenu(state: ReviewState, imageId: Long, markIndex: Int?, afterChoice: () -> Unit): List<MenuEntry> {
    val markEntries = if (markIndex == null) emptyList() else listOf(
        MenuEntry("Select", Icons.MousePointer, onSelect = { state.selectedMark = markIndex; afterChoice() }),
        MenuEntry("Delete mark", Icons.Trash, danger = true, onSelect = { state.deleteMark(imageId, markIndex); afterChoice() }),
    )
    val toolEntries = drawingTools.mapIndexed { i, tool ->
        MenuEntry(toolName(tool), toolIcon(tool), hint = toolHotkey(tool), dividerAbove = i == 0 && markEntries.isNotEmpty(), onSelect = { state.arm(tool); afterChoice() })
    }
    return markEntries + toolEntries
}
