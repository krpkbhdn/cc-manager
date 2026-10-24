package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.event.KeyEvent.VK_1
import java.awt.event.KeyEvent.VK_C
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_F1
import java.awt.event.KeyEvent.VK_J
import java.awt.event.KeyEvent.VK_K
import java.awt.event.KeyEvent.VK_L
import java.awt.event.KeyEvent.VK_N
import java.awt.event.KeyEvent.VK_P
import java.awt.event.KeyEvent.VK_R
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_U
import java.awt.event.KeyEvent.VK_V
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShortcutResolverTest {
    private fun windows(chord: KeyChord, terminal: Boolean) = ShortcutResolver.resolve(chord, mac = false, terminalFocused = terminal)

    @Test
    fun `ctrl shift chords fire inside the terminal`() {
        assertThat(windows(ctrlShift(VK_P), terminal = true)).isEqualTo(AppShortcut.PALETTE)
        assertThat(windows(ctrlShift(VK_L), terminal = true)).isEqualTo(AppShortcut.TOGGLE_THEME)
    }

    @Test
    fun `plain ctrl letters stay with the terminal`() {
        listOf(VK_K, VK_J, VK_N, VK_U, VK_R).forEach { assertThat(windows(ctrl(it), terminal = true)).isNull() }
    }

    @Test
    fun `plain ctrl k and n work outside the terminal`() {
        assertThat(windows(ctrl(VK_K), terminal = false)).isEqualTo(AppShortcut.PALETTE)
        assertThat(windows(ctrl(VK_N), terminal = false)).isEqualTo(AppShortcut.ADD_PROJECT)
    }

    @Test
    fun `terminal copy and paste chords are never claimed`() {
        assertThat(windows(ctrlShift(VK_C), terminal = true)).isNull()
        assertThat(windows(ctrlShift(VK_V), terminal = true)).isNull()
        assertThat(windows(ctrlShift(VK_C), terminal = false)).isNull()
    }

    @Test
    fun `alt digits open projects and ctrl tab cycles tabs in the terminal`() {
        assertThat(windows(alt(VK_1), terminal = true)?.projectSlot).isEqualTo(1)
        assertThat(windows(ctrl(VK_TAB), terminal = true)).isEqualTo(AppShortcut.NEXT_TAB)
        assertThat(windows(ctrlShift(VK_TAB), terminal = true)).isEqualTo(AppShortcut.PREVIOUS_TAB)
    }

    @Test
    fun `altgr style ctrl plus alt is not an app shortcut`() {
        assertThat(windows(KeyChord(VK_1, ctrl = true, alt = true), terminal = false)).isNull()
    }

    @Test
    fun `escape and f1 resolve without modifiers`() {
        assertThat(windows(plain(VK_ESCAPE), terminal = true)).isEqualTo(AppShortcut.CLOSE_OVERLAY)
        assertThat(windows(plain(VK_F1), terminal = true)).isEqualTo(AppShortcut.SHORTCUT_SHEET)
    }

    @Test
    fun `mac uses cmd for the palette and ignores the windows chord`() {
        assertThat(ShortcutResolver.resolve(cmd(VK_K), mac = true, terminalFocused = true)).isEqualTo(AppShortcut.PALETTE)
        assertThat(ShortcutResolver.resolve(ctrlShift(VK_P), mac = true, terminalFocused = false)).isNull()
    }

    @Test
    fun `no two shortcuts share a chord on any platform`() {
        listOf(false, true).forEach { mac ->
            val chords = AppShortcut.entries.flatMap { it.chords(mac) }
            assertThat(chords).doesNotHaveDuplicates()
        }
    }

    @Test
    fun `hints show the primary chord`() {
        assertThat(AppShortcut.PALETTE.hint(mac = false)).isEqualTo("Ctrl+Shift+P")
        assertThat(AppShortcut.PALETTE.hint(mac = true)).isEqualTo("Cmd+K")
        assertThat(AppShortcut.OVERVIEW.keys(mac = false)).containsExactly("Alt", "0")
        assertThat(AppShortcut.FOCUS_TERMINAL.hint(mac = false)).isEqualTo("Ctrl+Shift+`")
    }
}
