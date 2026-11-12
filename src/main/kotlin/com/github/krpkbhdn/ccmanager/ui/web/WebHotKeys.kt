package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.review.ReviewTool
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** What a plain key on the focused web canvas asks for; [WebState.perform] carries it out. */
sealed interface WebAction {
    data object TogglePick : WebAction
    data object StickyPick : WebAction
    data object QuickFeedback : WebAction
    data object ToggleDrawing : WebAction
    data object ToggleOverlays : WebAction
    data object ToggleLock : WebAction
    data object Rotate : WebAction
    data object ZoomFit : WebAction
    data object Zoom100 : WebAction
    data object ZoomIn : WebAction
    data object ZoomOut : WebAction
    data object DeleteMark : WebAction
    data object Confirm : WebAction
    data object CloseMenu : WebAction
    data object CancelGesture : WebAction
    data object DisarmTool : WebAction
    data object StopPick : WebAction
    data object ExitDrawing : WebAction
    data class ToggleGroup(val group: OverlayGroup) : WebAction
    data class SelectTool(val tool: ReviewTool) : WebAction
}

/** The app modes that decide what a key means; [menuOpen] is a context menu or popover, [gestureRunning] a mark being drawn. */
data class HotKeyModes(
    val interaction: Boolean = true,
    val drawing: Boolean = false,
    val picking: Boolean = false,
    val menuOpen: Boolean = false,
    val gestureRunning: Boolean = false,
    val toolArmed: Boolean = false,
)

/** [swallow]: the key was taken (or is the typed/released echo of one that was), so the page must not see it. */
data class HotKeyOutcome(val action: WebAction?, val swallow: Boolean)

/** Plain-key map of the web canvas; any chord with Ctrl/Alt/Meta is not ours, and nothing fires while the page edits text (unless the page is locked). */
object WebHotKeys {
    private const val FOREIGN_MODIFIERS = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
    private val GROUPS = OverlayGroup.entries.associateBy { it.key }

    fun resolve(key: Int, modifiers: Int, modes: HotKeyModes, pageEditing: Boolean): WebAction? {
        if (modifiers and FOREIGN_MODIFIERS != 0) return null
        if (pageEditing && modes.interaction) return null
        val shift = modifiers and InputEvent.SHIFT_DOWN_MASK != 0
        if (key == KeyEvent.VK_ESCAPE) return if (shift) null else escape(modes)
        return (if (modes.drawing && !shift) drawingKey(key) else null) ?: globalKey(key, shift)
    }

    /** Nearest thing to close first; null leaves Esc to the page. */
    private fun escape(modes: HotKeyModes): WebAction? = when {
        modes.menuOpen -> WebAction.CloseMenu
        modes.gestureRunning -> WebAction.CancelGesture
        modes.toolArmed -> WebAction.DisarmTool
        modes.picking -> WebAction.StopPick
        modes.drawing -> WebAction.ExitDrawing
        else -> null
    }

    private fun drawingKey(key: Int): WebAction? = when (key) {
        KeyEvent.VK_V -> WebAction.SelectTool(ReviewTool.POINTER)
        KeyEvent.VK_R -> WebAction.SelectTool(ReviewTool.RECTANGLE)
        KeyEvent.VK_E -> WebAction.SelectTool(ReviewTool.ELLIPSE)
        KeyEvent.VK_P -> WebAction.SelectTool(ReviewTool.PIN)
        KeyEvent.VK_F -> WebAction.SelectTool(ReviewTool.FREEFORM)
        KeyEvent.VK_DELETE ->WebAction.DeleteMark
        KeyEvent.VK_ENTER -> WebAction.Confirm
        else -> null
    }

    private fun globalKey(key: Int, shift: Boolean): WebAction? = when (key) {
        KeyEvent.VK_I -> if (shift) WebAction.StickyPick else WebAction.TogglePick
        KeyEvent.VK_0 -> if (shift) WebAction.Zoom100 else WebAction.ZoomFit
        KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> WebAction.ZoomIn
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> WebAction.ZoomOut
        else -> if (shift) null else plainKey(key)
    }

    private fun plainKey(key: Int): WebAction? = when (key) {
        KeyEvent.VK_Q -> WebAction.QuickFeedback
        KeyEvent.VK_D -> WebAction.ToggleDrawing
        KeyEvent.VK_O -> WebAction.ToggleOverlays
        KeyEvent.VK_L -> WebAction.ToggleLock
        KeyEvent.VK_T -> WebAction.Rotate
        in KeyEvent.VK_1..KeyEvent.VK_5 -> GROUPS[('0' + (key - KeyEvent.VK_0))]?.let(WebAction::ToggleGroup)
        else -> null
    }
}

/** Remembers which key presses became actions so their typed and released events (and auto-repeats) never reach the page; a typed event not right after a consumed press (IME commit, dead key) is the page's. */
class HotKeyGuard {
    private val consumed = mutableSetOf<Int>()
    private var typedFollowsConsumed = false

    fun onKey(awtId: Int, keyCode: Int, modifiers: Int, modes: HotKeyModes, pageEditing: Boolean): HotKeyOutcome = when (awtId) {
        KeyEvent.KEY_PRESSED -> pressed(keyCode, modifiers, modes, pageEditing)
        KeyEvent.KEY_TYPED -> HotKeyOutcome(null, typedFollowsConsumed.also { typedFollowsConsumed = false })
        KeyEvent.KEY_RELEASED -> HotKeyOutcome(null, consumed.remove(keyCode).also { typedFollowsConsumed = false })
        else -> HotKeyOutcome(null, false)
    }

    fun reset() {
        consumed.clear()
        typedFollowsConsumed = false
    }

    private fun pressed(keyCode: Int, modifiers: Int, modes: HotKeyModes, pageEditing: Boolean): HotKeyOutcome {
        if (keyCode in consumed) {
            typedFollowsConsumed = true
            return HotKeyOutcome(null, true)
        }
        val action = WebHotKeys.resolve(keyCode, modifiers, modes, pageEditing)
        typedFollowsConsumed = action != null
        if (action != null) consumed += keyCode
        return HotKeyOutcome(action, action != null)
    }
}
