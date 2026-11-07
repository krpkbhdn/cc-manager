package com.github.krpkbhdn.ccmanager.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.history.SessionSearchService.SearchHit
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.CardRow
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private const val SNIPPET_LINES = 3
private const val ALL_PROJECTS = "All projects"
private const val OTHER_PROJECT = "Other"
private const val INDEX_NOTE = "Secrets in the history are masked by a heuristic, with no guarantee that every one is caught."

private fun Instant.local(): String = timeFormat.format(atZone(ZoneId.systemDefault()))

/** Full-text search over past Claude conversations, with Open (transcript at the hit), Resume and Copy per result. */
@Composable
fun SessionSearchScreen(search: SessionSearchState, projects: List<Project>, onResume: (Project, SearchHit) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Search sessions", Modifier.weight(1f), style = Type.heading)
            CountBadge(search.hits.size)
        }
        HorizontalRule()
        Filters(search, projects, focus)
        Label("${"%,d".format(search.indexedEntries)} entries indexed. $INDEX_NOTE", Modifier.padding(horizontal = Spacing.lg), color = colors.textTertiary, style = Type.small, maxLines = 2)
        Box(Modifier.weight(1f)) { Results(search, projects, onResume) }
    }
    search.opened?.let { TranscriptViewerDialog("${projectOfCwd(it.hit.cwd, projects)?.name ?: it.hit.cwd} · ${it.hit.at.local()}", it.window, search::closeTranscript) }
}

@Composable
private fun Filters(search: SessionSearchState, projects: List<Project>, focus: FocusRequester) {
    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(search.query, Modifier.fillMaxWidth(), placeholder = "Search what was said or done in past sessions", icon = Icons.Search, focusRequester = focus)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ProjectFilter(search.project, projects) { search.project = it }
            SegmentedControl(SearchPeriod.entries.map { Segment(it, it.label) }, search.period, { search.period = it })
            Box(Modifier.weight(1f))
            if (search.project != null) AppButton("Clear session history", search::requestClear, icon = Icons.Trash, compact = true, tooltip = "Forget the indexed history of this project")
        }
    }
}

@Composable
private fun ProjectFilter(selected: Project?, projects: List<Project>, onSelect: (Project?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(selected?.name ?: ALL_PROJECTS, { open = !open }, icon = Icons.Folder)
        if (open) {
            val entries = listOf(MenuEntry(ALL_PROJECTS, if (selected == null) Icons.Check else null) { onSelect(null) }) +
                projects.map { MenuEntry(it.name, if (selected?.id == it.id) Icons.Check else null) { onSelect(it) } }
            PopupMenu(entries, { open = false })
        }
    }
}

@Composable
private fun Results(search: SessionSearchState, projects: List<Project>, onResume: (Project, SearchHit) -> Unit) {
    val error = search.error
    when {
        error != null -> EmptyState(Icons.CircleAlert, "Search failed", error, tint = colors.danger)
        search.query.text.isBlank() -> EmptyState(Icons.Search, "Find a past conversation", "Type words from a prompt, an answer or a command. Only sessions Claude Code has already written to disk are searched.")
        search.hits.isEmpty() -> EmptyState(Icons.Search, if (search.searching) "Searching" else "Nothing found", if (search.searching) "" else "Try fewer words, another project or a longer period.")
        else -> HitList(search, projects, onResume)
    }
}

@Composable
private fun HitList(search: SessionSearchState, projects: List<Project>, onResume: (Project, SearchHit) -> Unit) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(search.hits, key = { "${it.path}:${it.lineOffset}:${it.entryUuid}" }) { hit ->
                HitRow(hit, projectOfCwd(hit.cwd, projects), { search.open(hit) }, onResume)
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun HitRow(hit: SearchHit, project: Project?, onOpen: () -> Unit, onResume: (Project, SearchHit) -> Unit) {
    CardRow {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(project?.name ?: "$OTHER_PROJECT (${hit.cwd})", Modifier.weight(1f, fill = false), style = Type.bodyMedium, tooltipWhenTruncated = true)
                Label("${hit.role} · ${hit.at.local()}", color = colors.textTertiary, style = Type.small)
            }
            Snippet(hit.snippet)
        }
        AppButton("Open", onOpen, icon = Icons.Eye, compact = true, tooltip = "Open the transcript at this place")
        AppButton("Resume", { project?.let { onResume(it, hit) } }, icon = Icons.Play, compact = true, enabled = project != null, tooltip = if (project == null) "No project is registered for this directory" else "Continue this conversation")
        IconButton(Icons.Copy, { copyToClipboard(plainSnippet(hit.snippet)) }, tooltip = "Copy the snippet")
    }
}

@Composable
private fun Snippet(raw: String) {
    val matchStyle = SpanStyle(background = colors.accent.copy(alpha = Alpha.SOFT), fontWeight = FontWeight.SemiBold)
    val text = buildAnnotatedString {
        parseSnippet(raw).forEach { part -> if (part.match) withStyle(matchStyle) { append(part.text) } else append(part.text) }
    }
    BasicText(text, style = Type.small.copy(color = colors.textSecondary), maxLines = SNIPPET_LINES, overflow = TextOverflow.Ellipsis)
}
