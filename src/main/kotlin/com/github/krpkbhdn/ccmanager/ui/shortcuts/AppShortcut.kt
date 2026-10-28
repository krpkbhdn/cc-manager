package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.event.KeyEvent.VK_0
import java.awt.event.KeyEvent.VK_1
import java.awt.event.KeyEvent.VK_2
import java.awt.event.KeyEvent.VK_3
import java.awt.event.KeyEvent.VK_4
import java.awt.event.KeyEvent.VK_5
import java.awt.event.KeyEvent.VK_6
import java.awt.event.KeyEvent.VK_7
import java.awt.event.KeyEvent.VK_8
import java.awt.event.KeyEvent.VK_9
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_B
import java.awt.event.KeyEvent.VK_BACK_QUOTE
import java.awt.event.KeyEvent.VK_COMMA
import java.awt.event.KeyEvent.VK_D
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_F1
import java.awt.event.KeyEvent.VK_F12
import java.awt.event.KeyEvent.VK_G
import java.awt.event.KeyEvent.VK_H
import java.awt.event.KeyEvent.VK_J
import java.awt.event.KeyEvent.VK_K
import java.awt.event.KeyEvent.VK_I
import java.awt.event.KeyEvent.VK_L
import java.awt.event.KeyEvent.VK_M
import java.awt.event.KeyEvent.VK_N
import java.awt.event.KeyEvent.VK_O
import java.awt.event.KeyEvent.VK_P
import java.awt.event.KeyEvent.VK_Q
import java.awt.event.KeyEvent.VK_R
import java.awt.event.KeyEvent.VK_S
import java.awt.event.KeyEvent.VK_SLASH
import java.awt.event.KeyEvent.VK_T
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_U
import java.awt.event.KeyEvent.VK_W
import java.awt.event.KeyEvent.VK_Y

/**
 * Every app-level shortcut with its chords per platform; the first chord is the one shown in hints. Chords that
 * clash with terminal apps (plain Ctrl+letter) only fire outside the terminal, see [ShortcutResolver].
 */
enum class AppShortcut(val label: String, private val windows: List<KeyChord>, private val mac: List<KeyChord>) {
    PALETTE("Command palette", listOf(ctrlShift(VK_P), ctrl(VK_K)), listOf(cmd(VK_K), cmdShift(VK_P))),
    ADD_PROJECT("Add project", listOf(ctrlShift(VK_O), ctrl(VK_N)), listOf(cmd(VK_O), cmd(VK_N))),
    NEW_SESSION("New Claude session", listOf(ctrlShift(VK_T)), listOf(cmd(VK_T))),
    ALL_SESSIONS("All sessions", listOf(ctrlShift(VK_S)), listOf(cmdShift(VK_S))),
    ASSISTANT("Assistant", listOf(ctrlShift(VK_I)), listOf(cmdShift(VK_I))),
    NEXT_WAITING("Answer next waiting session", listOf(ctrlShift(VK_N)), listOf(cmdShift(VK_N))),
    OPEN_TERMINAL("Open terminal", listOf(alt(VK_F12)), listOf(alt(VK_F12))),
    CLOSE_SESSION("Close session tab", listOf(ctrlShift(VK_W)), listOf(cmd(VK_W))),
    NEXT_TAB("Next tab", listOf(ctrl(VK_TAB)), listOf(ctrl(VK_TAB))),
    PREVIOUS_TAB("Previous tab", listOf(ctrlShift(VK_TAB)), listOf(ctrlShift(VK_TAB))),
    TOGGLE_SIDEBAR("Toggle sidebar", listOf(ctrlShift(VK_B)), listOf(cmd(VK_B))),
    TOGGLE_AGENTS("Toggle agents rail", listOf(ctrlShift(VK_J)), listOf(cmd(VK_J))),
    TOGGLE_QUICK_ACTIONS("Toggle quick actions", listOf(ctrlShift(VK_Q)), listOf(cmd(VK_U))),
    TOGGLE_THEME("Toggle theme", listOf(ctrlShift(VK_L)), listOf(cmdShift(VK_L))),
    OVERVIEW("Overview", listOf(alt(VK_0)), listOf(cmdShift(VK_0))),
    PROJECT_1("Project 1", listOf(alt(VK_1)), listOf(cmd(VK_1))),
    PROJECT_2("Project 2", listOf(alt(VK_2)), listOf(cmd(VK_2))),
    PROJECT_3("Project 3", listOf(alt(VK_3)), listOf(cmd(VK_3))),
    PROJECT_4("Project 4", listOf(alt(VK_4)), listOf(cmd(VK_4))),
    PROJECT_5("Project 5", listOf(alt(VK_5)), listOf(cmd(VK_5))),
    PROJECT_6("Project 6", listOf(alt(VK_6)), listOf(cmd(VK_6))),
    PROJECT_7("Project 7", listOf(alt(VK_7)), listOf(cmd(VK_7))),
    PROJECT_8("Project 8", listOf(alt(VK_8)), listOf(cmd(VK_8))),
    PROJECT_9("Project 9", listOf(alt(VK_9)), listOf(cmd(VK_9))),
    GIT_TAB("Git tab", listOf(ctrlShift(VK_G)), listOf(cmdShift(VK_G))),
    GIT_CHANGES("Git changes", listOf(ctrlShift(VK_D)), listOf(cmdShift(VK_D))),
    GIT_COMMIT("Commit changes", listOf(ctrlShift(VK_K)), listOf(cmdShift(VK_K))),
    GIT_BRANCHES("Switch branch", listOf(ctrlShift(VK_H)), listOf(cmdShift(VK_H))),
    GIT_PUSH("Push", listOf(ctrlShift(VK_U)), listOf(cmdShift(VK_U))),
    RUN("Run selected configuration", listOf(ctrlShift(VK_R)), listOf(cmdShift(VK_R))),
    ACTIVITY_TAB("Activity tab", listOf(ctrlShift(VK_A)), listOf(cmdShift(VK_A))),
    NOTIFICATIONS("Notifications", listOf(ctrlShift(VK_M)), listOf(cmdShift(VK_M))),
    USAGE("Show usage", listOf(ctrlShift(VK_Y)), listOf(cmdShift(VK_Y))),
    PROJECT_SETTINGS("Project settings", listOf(ctrlShift(VK_COMMA)), listOf(cmd(VK_COMMA))),
    FOCUS_TERMINAL("Focus terminal", listOf(ctrlShift(VK_BACK_QUOTE)), listOf(ctrl(VK_BACK_QUOTE))),
    SHORTCUT_SHEET("Keyboard shortcuts", listOf(ctrlShift(VK_SLASH), plain(VK_F1)), listOf(cmdShift(VK_SLASH), plain(VK_F1))),
    CLOSE_OVERLAY("Close overlay", listOf(plain(VK_ESCAPE)), listOf(plain(VK_ESCAPE)));

    fun chords(mac: Boolean = Platform.isMac): List<KeyChord> = if (mac) this.mac else windows

    /** Keycap labels of the primary chord, for [com.github.krpkbhdn.ccmanager.ui.components.Shortcut] chips. */
    fun keys(mac: Boolean = Platform.isMac): List<String> = chords(mac).first().keys()

    /** Primary chord as one string such as `Ctrl+Shift+P`, for tooltips. */
    fun hint(mac: Boolean = Platform.isMac): String = keys(mac).joinToString("+")

    /** 1-based slot of the PROJECT_n shortcuts, null for the others. */
    val projectSlot: Int? get() = name.removePrefix(PROJECT_PREFIX).toIntOrNull()

    private companion object {
        const val PROJECT_PREFIX = "PROJECT_"
    }
}

object Platform {
    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")
}
