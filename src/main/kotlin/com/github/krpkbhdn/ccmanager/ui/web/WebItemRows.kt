package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.web.DeviceCategory
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebPage
import com.github.krpkbhdn.ccmanager.web.WebRevision

/** One line of the Items list: a revision header, a page heading or an item. */
sealed interface ItemsRow {
    val key: String

    data class Header(val revision: WebRevision, val openCount: Int, val total: Int, val shown: Int, val expanded: Boolean, val startsNext: Boolean) : ItemsRow {
        override val key get() = "revision-${revision.id()}"
    }

    data class PageHeading(val revisionId: Long, val pageId: Long, val title: String) : ItemsRow {
        override val key get() = "page-$revisionId-$pageId"
    }

    data class Entry(val item: WebItem) : ItemsRow {
        override val key get() = "item-${item.id()}"
    }
}

/**
 * Newest revision first. The newest one is expanded and older ones collapsed, and [toggled] flips that per revision.
 * Items are grouped by page in the order the pages first appear.
 * Header counts cover every item (Send transmits the whole revision); only the listed entries follow the device [scope].
 */
fun itemRows(revisions: List<WebRevision>, itemsOf: (Long) -> List<WebItem>, pages: List<WebPage>, toggled: Set<Long>, scope: DeviceCategory? = null): List<ItemsRow> =
    revisions.flatMapIndexed { index, revision ->
        val items = itemsOf(revision.id())
        val expanded = (revision.id() in toggled) != (index == 0)
        val shown = items.visibleIn(scope)
        val header = ItemsRow.Header(revision, items.count { it.status() == WebItemStatus.OPEN }, items.size, shown.size, expanded, index == 0 && revision.status() != ReviewStatus.PENDING)
        if (!expanded) listOf(header) else listOf<ItemsRow>(header) + groupByPage(revision.id(), shown, pages)
    }

private fun groupByPage(revisionId: Long, items: List<WebItem>, pages: List<WebPage>): List<ItemsRow> =
    items.groupBy { it.pageId() }.flatMap { (pageId, onPage) ->
        val page = pages.firstOrNull { it.id() == pageId }
        val title = page?.let { it.title().ifBlank { it.pageKey() } } ?: "Page"
        listOf<ItemsRow>(ItemsRow.PageHeading(revisionId, pageId, title)) + onPage.map { ItemsRow.Entry(it) }
    }
