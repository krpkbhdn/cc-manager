package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.web.WebItem

/** Delete on the focused item row; only a plain Delete counts (no modifier, never Backspace), and the page never sees the key. */
internal object ItemKeys {
    /** True when the key deleted [item] and was used up. */
    fun onKey(view: WebView, item: WebItem, key: Key, modified: Boolean, delete: (WebItem) -> Unit): Boolean {
        if (key != Key.Delete || modified) return false
        delete(item)
        if (view.selectedItemId == item.id()) view.selectedItemId = null
        view.itemPopover = null
        return true
    }
}
