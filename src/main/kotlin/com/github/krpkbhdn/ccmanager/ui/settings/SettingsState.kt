package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pages of the app settings dialog in the order the category list shows them. */
enum class SettingsCategory(val label: String) {
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    SESSIONS("Sessions"),
    USAGE("Usage"),
    ASSISTANT("Assistant"),
    VOICE("Voice input"),
    UPDATES("Updates"),
    AUTHOR("Author configuration"),
    ABOUT("About"),
    ;

    /** The category [delta] steps away, stopping at both ends of the list. */
    fun step(delta: Int): SettingsCategory = entries[(ordinal + delta).coerceIn(0, entries.lastIndex)]
}

/** Whether the app settings dialog is open and which category it shows; the category is kept until the app exits. */
class SettingsState {
    var open by mutableStateOf(false)
        private set
    var category by mutableStateOf(SettingsCategory.APPEARANCE)
        private set

    fun show() {
        open = true
    }

    fun close() {
        open = false
    }

    fun select(target: SettingsCategory) {
        category = target
    }

    fun move(delta: Int) {
        category = category.step(delta)
    }
}
