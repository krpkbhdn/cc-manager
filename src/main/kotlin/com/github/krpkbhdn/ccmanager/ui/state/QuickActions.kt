package com.github.krpkbhdn.ccmanager.ui.state

object Keys {
    const val ESCAPE = "\u001b"
    const val ENTER = "\r"
    const val SHIFT_TAB = "\u001b[Z"
}

/** A console shortcut: [keys] are typed into the session terminal as is. */
data class QuickAction(val label: String, val keys: String, val hint: String)

val quickActions = listOf(
    QuickAction("Interrupt", Keys.ESCAPE, "Esc"),
    QuickAction("Compact", "/compact${Keys.ENTER}", "/compact"),
    QuickAction("Clear", "/clear${Keys.ENTER}", "/clear"),
    QuickAction("Resume", "/resume${Keys.ENTER}", "/resume"),
    QuickAction("Plan mode", Keys.SHIFT_TAB, "Shift+Tab"),
    QuickAction("Model", "/model${Keys.ENTER}", "/model"),
    QuickAction("Agents", "/agents${Keys.ENTER}", "/agents"),
    QuickAction("Help", "/help${Keys.ENTER}", "/help"),
)
