package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.review.ReviewService.CaptureTarget
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val NEW_REVIEW_LABEL = "New review"
private const val TITLE_CHARS = 24

private fun CaptureTarget.count() = "$images/$maxImages"

/** Chip text: the pinned review with its screenshot count, or "New review" when the next screenshot starts one. */
internal fun targetChipText(targets: List<CaptureTarget>, pinned: Long?, stale: Boolean): String {
    val target = targets.firstOrNull { it.id() == pinned }
    return when {
        stale -> "Review: ${target?.title()?.take(TITLE_CHARS) ?: "?"} (${if (target == null) "closed" else "full"})"
        target == null -> NEW_REVIEW_LABEL
        else -> "Review: ${target.title().take(TITLE_CHARS)} ${target.count()}"
    }
}

/** "New review" first, then one row per pending review of the project; a full review is disabled. */
internal fun targetEntries(targets: List<CaptureTarget>, pinned: Long?, pin: (Long) -> Unit, unpin: () -> Unit): List<MenuEntry> =
    listOf(MenuEntry("New review on next screenshot", checked = pinned == null, onSelect = unpin)) +
        targets.map { MenuEntry(it.title(), hint = it.count(), enabled = !it.full(), checked = it.id() == pinned, dividerAbove = it === targets.first()) { pin(it.id()) } }

/** Toolbar dropdown choosing which review the next screenshot joins; selecting "New review" makes the next one create it. */
@Composable
internal fun WebReviewTargetMenu(projectId: Long, state: WebState) {
    val targets = state.captureTargets(projectId)
    val pinned = state.pinnedReview(projectId)
    val stale = state.pinIsStale(projectId)
    DropdownChip(
        targetChipText(targets, pinned, stale),
        targetEntries(targets, pinned, { state.pin(projectId, it) }, { state.unpin(projectId) }),
        color = if (stale) colors.warning else colors.textPrimary,
    )
}
