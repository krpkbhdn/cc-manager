package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.git.branch.BranchesView
import com.github.krpkbhdn.ccmanager.ui.git.changes.ChangesView
import com.github.krpkbhdn.ccmanager.ui.git.log.LogView
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.changeSections
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant

private const val MONO_PROGRESS_LINES = 1

/** The project's Git tab: toolbar, banners, a section switcher and the section itself, all driven by one [GitState]. */
@Composable
fun GitTab(git: GitState, system: GitSystemActions, now: Instant, modifier: Modifier = Modifier) {
    val status = git.status
    val loadError = git.loadError
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (status == null || status.repository()) GitToolbar(git)
            JobLine(git)
            GitBanners(git)
            when {
                status == null && loadError == null -> TabSkeleton()
                status == null -> EmptyState(Icons.Alert, loadError!!.title, loadError.headline, tint = colors.danger)
                !status.repository() -> EmptyState(Icons.Branch, "Not a git repository", "Run git init in this folder to track history, branches and changes here.")
                else -> Sections(git, system, now)
            }
        }
    }
}

@Composable
private fun Sections(git: GitState, system: GitSystemActions, now: Instant) {
    HorizontalRule()
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SegmentedControl(segments(git), git.view, git::show)
    }
    HorizontalRule()
    Crossfade(git.view, Modifier.fillMaxSize(), animationSpec = Motion.base(), label = "gitSection") { view ->
        when (view) {
            GitSubView.CHANGES -> ChangesView(git, system)
            GitSubView.LOG -> LogView(git, now)
            GitSubView.BRANCHES -> BranchesView(git)
            GitSubView.STASHES -> StashesView(git, now)
            GitSubView.TAGS -> TagsView(git)
        }
    }
}

private fun segments(git: GitState): List<Segment<GitSubView>> {
    val dirty = changeSections(git.status?.changes().orEmpty()).dirtyCount
    return listOf(
        Segment(GitSubView.CHANGES, GitSubView.CHANGES.label, Icons.Diff, dirty),
        Segment(GitSubView.LOG, GitSubView.LOG.label, Icons.Log),
        Segment(GitSubView.BRANCHES, GitSubView.BRANCHES.label, Icons.Branch, git.branches.count { !it.remote() }),
        Segment(GitSubView.STASHES, GitSubView.STASHES.label, Icons.Archive, git.stashes.size),
        Segment(GitSubView.TAGS, GitSubView.TAGS.label, Icons.Tag, git.tags.size),
    )
}

/** Spinner and git's latest progress line while a mutation runs. */
@Composable
private fun JobLine(git: GitState) {
    val job = git.job
    AnimatedVisibility(job != null, enter = expandVertically(Motion.base()) + fadeIn(Motion.base()), exit = shrinkVertically(Motion.fast()) + fadeOut(Motion.fast())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Spinner(size = 12.dp)
            Label("${job?.label.orEmpty()}...", color = colors.textSecondary, style = Type.small)
            job?.progress?.let { Label(it, Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall, maxLines = MONO_PROGRESS_LINES) }
        }
    }
}

@Composable
private fun TabSkeleton() {
    Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(SKELETON_ROWS) { SkeletonBlock(Modifier.fillMaxWidth(0.3f + it % 3 * 0.2f).height(18.dp)) }
    }
}

private const val SKELETON_ROWS = 6

