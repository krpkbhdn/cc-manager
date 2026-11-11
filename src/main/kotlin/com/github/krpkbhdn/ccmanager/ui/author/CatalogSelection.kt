package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorArea
import com.github.krpkbhdn.ccmanager.author.AuthorCatalog
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus

/** The left-pane areas of the catalog dialog. */
enum class CatalogTab(val title: String, val subtitle: String) {
    TOOLS("Claude tools", "Marketplaces, plugins, skills, MCP servers"), CONFIG("Configuration","Settings and agent teams"),
}

fun AuthorArea.tab(): CatalogTab = when (this) {
    AuthorArea.CLAUDE_TOOLS -> CatalogTab.TOOLS
    AuthorArea.CLAUDE_CONFIG -> CatalogTab.CONFIG
}

enum class Check { NONE, SOME, ALL }

/** One section of cards; [key] is unique across areas, [title] carries the area while searching. */
class CatalogGroup(val key: String, val title: String, val entries: List<AuthorEntry>)

/** A line of the right pane: a group header or one of its cards. */
sealed interface CatalogRow {
    data class Header(val group: CatalogGroup) : CatalogRow
    data class Card(val entry: AuthorEntry) : CatalogRow
}

private const val NOTHING_NEW = "Nothing new or updated here. Switch off \"Only new and updated\" to see what is installed."
private const val NO_MATCH = "No items match your search."

/** Pure selection rules of the catalog dialog: requirement closure, tri-state groups, search and counts. */
object CatalogSelection {

    /** An installed item has nothing to install; every other status can be checked. */
    fun selectable(entry: AuthorEntry): Boolean = entry.status() != AuthorStatus.INSTALLED

    fun installedIds(entries: List<AuthorEntry>): Set<String> = entries.filterNot(::selectable).map { it.item().id() }.toSet()

    /** Checking adds what the item requires that is not installed yet; unchecking removes it and everything that requires it. */
    fun check(catalog: AuthorCatalog, selected: Set<String>, id: String, on: Boolean, installed: Set<String> = emptySet()): Set<String> =
        if (on) selected + catalog.requiresClosure(listOf(id)).map { it.id() }.filter { it !in installed }
        else selected - id - dependentsOf(catalog, id)

    fun checkAll(catalog: AuthorCatalog, selected: Set<String>, ids: Collection<String>, on: Boolean, installed: Set<String> = emptySet()): Set<String> =
        ids.fold(selected) { acc, id -> check(catalog, acc, id, on, installed) }

    fun triState(selected: Set<String>, ids: Collection<String>): Check {
        val checked = ids.count { it in selected }
        return when {
            checked == 0 -> Check.NONE
            checked == ids.size -> Check.ALL
            else -> Check.SOME
        }
    }

    /** The titles of what [id] also installs: its requirements, transitively, that are not installed yet. */
    fun alsoInstalls(catalog: AuthorCatalog, id: String, installed: Set<String>): List<String> =
        if (catalog.byId(id).isEmpty) emptyList()
        else catalog.requiresClosure(listOf(id)).filter { it.id() != id && it.id() !in installed }.map { it.title() }

    /** A non-blank [query] searches every area; otherwise only [tab] is listed. */
    fun visible(entries: List<AuthorEntry>, tab: CatalogTab, query: String, onlyNew: Boolean): List<AuthorEntry> {
        val needle = query.trim()
        return entries.filter { entry ->
            (needle.isNotEmpty() || entry.item().area().tab() == tab) &&
                (needle.isEmpty() || matches(entry, needle)) &&
                (!onlyNew || entry.status().offered())
        }
    }

    /** Titles carry the area only when a search spans both areas, so two groups of the same name stay apart. */
    fun groups(visible: List<AuthorEntry>, searching: Boolean): List<CatalogGroup> {
        val spansAreas = searching && visible.map { it.item().area() }.distinct().size > 1
        return visible.groupBy { it.item().area() to it.item().group() }
            .map { (key, entries) ->
                val (area, name) = key
                CatalogGroup("$area/$name", if (spansAreas) "${area.tab().title}: $name" else name, entries)
            }
    }

    fun rows(groups: List<CatalogGroup>): List<CatalogRow> = groups.flatMap { group ->
        listOf<CatalogRow>(CatalogRow.Header(group)) + group.entries.map { CatalogRow.Card(it) }
    }

    /** How many items of [tab] wait for a decision: new or updatable. */
    fun available(entries: List<AuthorEntry>, tab: CatalogTab): Int = entries.count { it.item().area().tab() == tab && it.status().offered() }

    fun available(group: CatalogGroup): Int = group.entries.count { it.status().offered() }

    /** The tab to open on: the first one with something waiting, else the first. */
    fun firstTabWithNews(entries: List<AuthorEntry>): CatalogTab = CatalogTab.entries.firstOrNull { available(entries, it) > 0 } ?: CatalogTab.TOOLS

    /** Unchecked items still waiting for a decision. */
    fun toDecline(entries: List<AuthorEntry>, selected: Set<String>): List<AuthorEntry> =
        entries.filter { it.item().id() !in selected && it.status().offered() }

    fun emptyMessage(searching: Boolean, onlyNew: Boolean): String = if (onlyNew && !searching) NOTHING_NEW else NO_MATCH

    private fun dependentsOf(catalog: AuthorCatalog, id: String): Set<String> =
        catalog.items().filter { item -> item.id() != id && catalog.requiresClosure(listOf(item.id())).any { it.id() == id } }.map { it.id() }.toSet()

    private fun matches(entry: AuthorEntry, needle: String): Boolean =
        listOf(entry.item().title(), entry.item().description(), entry.item().group()).any { it.contains(needle, ignoreCase = true) }
}
