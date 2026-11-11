package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.author.AuthorCatalog
import com.github.krpkbhdn.ccmanager.author.AuthorEntry

/** What the open catalog dialog holds: the items, the checks, the search and the focused area. [offer] is the start-up offer, not manage mode. */
class CatalogView(val catalog: AuthorCatalog, entries: List<AuthorEntry>, val offer: Boolean) {
    var entries by mutableStateOf(entries)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
    var tab by mutableStateOf(CatalogSelection.firstTabWithNews(entries))
    var onlyNew by mutableStateOf(offer)
    var focusedGroup by mutableStateOf<String?>(null)
    val query = TextFieldState()

    val searching get() = query.text.isNotBlank()
    val visible get() = CatalogSelection.visible(entries, tab, query.text.toString(), onlyNew)
    val groups get() = CatalogSelection.groups(visible, searching)

    fun check(id: String, on: Boolean) {
        selected = CatalogSelection.check(catalog, selected, id, on, CatalogSelection.installedIds(entries))
    }

    fun checkAll(ids: Collection<String>, on: Boolean) {
        selected = CatalogSelection.checkAll(catalog, selected, ids, on, CatalogSelection.installedIds(entries))
    }

    /** Jumps to a group of the middle pane: asks the right pane to scroll there. */
    fun focus(group: CatalogGroup) {
        focusedGroup = group.key
    }

    /** Takes freshly loaded [entries]; checks of items that are gone or now installed are dropped. */
    fun reload(entries: List<AuthorEntry>) {
        this.entries = entries
        val open = entries.filter(CatalogSelection::selectable).map { it.item().id() }.toSet()
        selected = selected.filter { it in open }.toSet()
    }
}
