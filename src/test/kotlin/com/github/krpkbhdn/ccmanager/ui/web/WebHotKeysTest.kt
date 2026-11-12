package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.review.ReviewTool
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebHotKeysTest {
    private val idle = HotKeyModes()
    private val drawing = HotKeyModes(drawing = true)
    private val none = 0
    private val shift = InputEvent.SHIFT_DOWN_MASK

    private fun resolve(key: Int, modifiers: Int = none, modes: HotKeyModes = idle, editing: Boolean = false) =
        WebHotKeys.resolve(key, modifiers, modes, editing)

    @Test
    fun `plain keys map to their actions`() {
        assertThat(resolve(KeyEvent.VK_I)).isEqualTo(WebAction.TogglePick)
        assertThat(resolve(KeyEvent.VK_I, shift)).isEqualTo(WebAction.StickyPick)
        assertThat(resolve(KeyEvent.VK_Q)).isEqualTo(WebAction.QuickFeedback)
        assertThat(resolve(KeyEvent.VK_D)).isEqualTo(WebAction.ToggleDrawing)
        assertThat(resolve(KeyEvent.VK_O)).isEqualTo(WebAction.ToggleOverlays)
        assertThat(resolve(KeyEvent.VK_L)).isEqualTo(WebAction.ToggleLock)
        assertThat(resolve(KeyEvent.VK_T)).isEqualTo(WebAction.Rotate)
        assertThat(resolve(KeyEvent.VK_0)).isEqualTo(WebAction.ZoomFit)
        assertThat(resolve(KeyEvent.VK_0, shift)).isEqualTo(WebAction.Zoom100)
        assertThat(resolve(KeyEvent.VK_EQUALS, shift)).isEqualTo(WebAction.ZoomIn)
        assertThat(resolve(KeyEvent.VK_MINUS)).isEqualTo(WebAction.ZoomOut)
        assertThat(resolve(KeyEvent.VK_1)).isEqualTo(WebAction.ToggleGroup(OverlayGroup.INSPECT))
        assertThat(resolve(KeyEvent.VK_5)).isEqualTo(WebAction.ToggleGroup(OverlayGroup.CONTRAST))
        assertThat(resolve(KeyEvent.VK_6)).isNull()
        assertThat(resolve(KeyEvent.VK_1, shift)).isNull()
    }

    @Test
    fun `a chord with Ctrl Alt or Meta passes through`() {
        listOf(InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK).forEach {
            assertThat(resolve(KeyEvent.VK_I, it)).isNull()
            assertThat(resolve(KeyEvent.VK_ESCAPE, it, HotKeyModes(drawing = true))).isNull()
        }
    }

    @Test
    fun `nothing fires while the page edits text unless the page is locked`() {
        assertThat(resolve(KeyEvent.VK_I, editing = true)).isNull()
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = drawing, editing = true)).isNull()
        assertThat(resolve(KeyEvent.VK_I, modes = HotKeyModes(interaction = false), editing = true)).isEqualTo(WebAction.TogglePick)
    }

    @Test
    fun `tool keys and mark keys exist only in drawing mode`() {
        assertThat(resolve(KeyEvent.VK_R)).isNull()
        assertThat(resolve(KeyEvent.VK_DELETE)).isNull()
        assertThat(resolve(KeyEvent.VK_V, modes = drawing)).isEqualTo(WebAction.SelectTool(ReviewTool.POINTER))
        assertThat(resolve(KeyEvent.VK_R, modes = drawing)).isEqualTo(WebAction.SelectTool(ReviewTool.RECTANGLE))
        assertThat(resolve(KeyEvent.VK_E, modes = drawing)).isEqualTo(WebAction.SelectTool(ReviewTool.ELLIPSE))
        assertThat(resolve(KeyEvent.VK_P, modes = drawing)).isEqualTo(WebAction.SelectTool(ReviewTool.PIN))
        assertThat(resolve(KeyEvent.VK_F, modes = drawing)).isEqualTo(WebAction.SelectTool(ReviewTool.FREEFORM))
        assertThat(resolve(KeyEvent.VK_DELETE, modes = drawing)).isEqualTo(WebAction.DeleteMark)
        assertThat(resolve(KeyEvent.VK_BACK_SPACE, modes = drawing)).isNull()
        assertThat(resolve(KeyEvent.VK_ENTER, modes = drawing)).isEqualTo(WebAction.Confirm)
        assertThat(resolve(KeyEvent.VK_D, modes = drawing)).isEqualTo(WebAction.ToggleDrawing)
    }

    @Test
    fun `Esc closes the nearest thing first and reaches the page only when no mode is active`() {
        val all = HotKeyModes(drawing = true, picking = true, menuOpen = true, gestureRunning = true, toolArmed = true)
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = all)).isEqualTo(WebAction.CloseMenu)
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = all.copy(menuOpen = false))).isEqualTo(WebAction.CancelGesture)
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = all.copy(menuOpen = false, gestureRunning = false))).isEqualTo(WebAction.DisarmTool)
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = HotKeyModes(drawing = true, picking = true))).isEqualTo(WebAction.StopPick)
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = drawing)).isEqualTo(WebAction.ExitDrawing)
        assertThat(resolve(KeyEvent.VK_ESCAPE)).isNull()
    }

    @Test
    fun `the typed and released echo of a consumed key is swallowed and others are not`() {
        val guard = HotKeyGuard()
        val pressed = guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_I, none, idle, false)
        assertThat(pressed).isEqualTo(HotKeyOutcome(WebAction.TogglePick, true))
        assertThat(guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_I, none, idle, false)).isEqualTo(HotKeyOutcome(null, true))
        assertThat(guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, false).swallow).isTrue()
        assertThat(guard.onKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_I, none, idle, false).swallow).isTrue()
        assertThat(guard.onKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_I, none, idle, false).swallow).isFalse()

        assertThat(guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_X, none, idle, false)).isEqualTo(HotKeyOutcome(null, false))
        assertThat(guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, false).swallow).isFalse()
        assertThat(guard.onKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_X, none, idle, false).swallow).isFalse()
    }

    @Test
    fun `an unconsumed Esc and a typed key in a text field reach the page`() {
        val guard = HotKeyGuard()
        assertThat(guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, none, idle, false)).isEqualTo(HotKeyOutcome(null, false))
        assertThat(guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_I, none, idle, true)).isEqualTo(HotKeyOutcome(null, false))
        assertThat(guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, true).swallow).isFalse()
    }

    @Test
    fun `Esc ends a plain pick in any mode`() {
        assertThat(resolve(KeyEvent.VK_ESCAPE, modes = HotKeyModes(interaction = false, picking = true))).isEqualTo(WebAction.StopPick)
    }

    @Test
    fun `a typed event with no consumed press right before it is not swallowed`() {
        val guard = HotKeyGuard()
        guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_I, none, idle, false)
        guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, false)
        assertThat(guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, false).swallow).isFalse()

        guard.onKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_Q, none, idle, false)
        guard.onKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_Q, none, idle, false)
        assertThat(guard.onKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, none, idle, false).swallow).isFalse()
    }
}
