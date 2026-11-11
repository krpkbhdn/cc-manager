package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.author.AuthorCatalog
import com.github.krpkbhdn.ccmanager.author.AuthorCatalogService
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorItem
import com.github.krpkbhdn.ccmanager.author.AuthorKind
import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private const val STATUS_CYCLE = 5
private const val EXTRA_ITEMS = 14
private const val LONG_DESCRIPTION = "Reviews every pull request against the team's conventions, runs the linters before it comments, groups findings by severity and links each one to the rule it breaks, so the author can fix the whole list in one pass instead of waiting for several review rounds."

/** Off until a shot turns it on, so no preview opens the author offer by itself. */
internal object PreviewAuthorStatuses {
    @Volatile
    var mixed = false

    @Volatile
    var crowded = false
}

/** The author catalog over the bundled profile with a mix of statuses, so the preview shows every card state; [PreviewAuthorStatuses.crowded] adds many long items. */
internal fun previewAuthorCatalog(): AuthorCatalogService {
    val bundled = AuthorProfileLoader().catalog()
    val service = mock(AuthorCatalogService::class.java)
    `when`(service.catalog()).thenAnswer { catalogNow(bundled) }
    `when`(service.entries()).thenAnswer {
        catalogNow(bundled).items().mapIndexed { index, item ->
            val status = when (if (PreviewAuthorStatuses.mixed) index % STATUS_CYCLE else 0) {
                0 -> AuthorStatus.INSTALLED
                1 -> AuthorStatus.UPDATE_AVAILABLE
                2 -> AuthorStatus.DECLINED
                else -> AuthorStatus.NOT_INSTALLED
            }
            val changes = if (status == AuthorStatus.UPDATE_AVAILABLE) listOf("Adds a new section to the rules", "Fixes the wording of the first rule") else emptyList()
            AuthorEntry(item, if (status == AuthorStatus.INSTALLED) item.version() else 0, status, changes, item.kind() == AuthorKind.TEAM)
        }
    }
    return service
}

private fun catalogNow(bundled: AuthorCatalog): AuthorCatalog {
    if (!PreviewAuthorStatuses.crowded) return bundled
    val template = bundled.items().first { it.kind() == AuthorKind.PLUGIN }
    val extra = (1..EXTRA_ITEMS).map { number ->
        AuthorItem(
            "extra.$number", template.area(), "Plugins", "Review assistant number $number with an unusually long title that has to be cut", LONG_DESCRIPTION, AuthorKind.PLUGIN, 1,
            listOf("marketplace.ponytail"), null, "extra$number", null, null, null, null, null,
        )
    }
    return AuthorCatalog(bundled.items() + extra, bundled.files())
}
