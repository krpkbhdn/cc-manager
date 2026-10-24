package com.github.krpkbhdn.ccmanager.ui.shortcuts

/** Maps a pressed chord to an app shortcut; chords a terminal app needs are left alone while the terminal has focus. */
object ShortcutResolver {
    private val windowsTable = tableFor(mac = false)
    private val macTable = tableFor(mac = true)

    fun resolve(chord: KeyChord, mac: Boolean, terminalFocused: Boolean): AppShortcut? {
        val shortcut = (if (mac) macTable else windowsTable)[chord] ?: return null
        return shortcut.takeUnless { terminalFocused && chord.clashesWithTerminalApps }
    }

    private fun tableFor(mac: Boolean): Map<KeyChord, AppShortcut> =
        AppShortcut.entries.flatMap { shortcut -> shortcut.chords(mac).map { it to shortcut } }.toMap()
}
