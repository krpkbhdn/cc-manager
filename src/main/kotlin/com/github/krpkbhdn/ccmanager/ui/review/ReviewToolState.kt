package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ReviewTool { POINTER, RECTANGLE, ELLIPSE, PIN, FREEFORM }

/** The canvas tool and the selected mark: a sticky default tool, a one-shot tool armed for the next gesture, the selection. */
internal class ReviewToolState {
    var tool by mutableStateOf(ReviewTool.POINTER)
    var oneShotTool by mutableStateOf<ReviewTool?>(null)
        private set
    var selectedMark by mutableStateOf<Int?>(null)

    /** The freeform polygon being placed; any tool change discards it. */
    val draft = PolygonDraft()

    val effectiveTool: ReviewTool get() = oneShotTool ?: tool

    /** The next gesture draws exactly one element of [kind], then the default tool is back. */
    fun arm(kind: ReviewTool) {
        draft.clear()
        oneShotTool = kind.takeIf { it != ReviewTool.POINTER }
    }

    fun clearOneShot() {
        draft.clear()
        oneShotTool = null
    }

    /** Esc: drops a polygon draft with its one-shot first, then an armed one-shot, then the selection; false when there was nothing to drop. */
    fun cancel(): Boolean {
        if (!draft.isEmpty) return true.also { clearOneShot() }
        if (oneShotTool != null) oneShotTool = null else if (selectedMark != null) selectedMark = null else return false
        return true
    }

    fun chooseTool(chosen: ReviewTool) {
        draft.clear()
        tool = chosen
        oneShotTool = null
    }
}
