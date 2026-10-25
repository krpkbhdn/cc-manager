package com.github.krpkbhdn.ccmanager.ui.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Banner
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.ExtensionFailure
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val outputMaxHeight = 160.dp

/** A failed CLI call: the message in a banner and, on request, the CLI's own output below it. */
@Composable
fun ExtensionFailureStrip(failure: ExtensionFailure, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showOutput by remember(failure) { mutableStateOf(false) }
    Column(modifier) {
        Banner("${failure.title}: ${failure.message}", colors.danger, Icons.CircleAlert) {
            if (failure.output.isNotBlank()) AppButton(if (showOutput) "Hide output" else "Show output", { showOutput = !showOutput }, compact = true)
            IconButton(Icons.Close, onDismiss, tooltip = "Dismiss")
        }
        if (showOutput) {
            Box(Modifier.fillMaxWidth().background(colors.terminal).heightIn(max = outputMaxHeight).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                Label(failure.output.trim(), color = colors.textSecondary, style = Type.monoSmall, maxLines = Int.MAX_VALUE)
            }
        }
    }
}

/** What the running CLI call is doing, shown in a page toolbar. */
@Composable
fun BusyLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Spinner(size = 14.dp)
        Label(text, color = colors.textSecondary, style = Type.small)
    }
}

/** Row card of an extension list; clickable and highlighted when [onClick] is given. */
@Composable
fun ListCard(modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val edge = if (selected) colors.accent.copy(alpha = Alpha.GLOW) else colors.border
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
    }
    if (onClick == null) {
        Box(modifier.fillMaxWidth().card(shape, colors.surface)) { body() }
    } else {
        Pressable(onClick, modifier.fillMaxWidth().border(1.dp, edge, shape), shape, if (selected) colors.surfaceSelected else colors.surface, if (selected) colors.surfaceSelected else colors.surfaceHover) { body() }
    }
}

@Composable
fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.lg, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(title, color = colors.textTertiary, style = Type.overline)
        CountBadge(count)
    }
}

@Composable
fun ExtensionAvatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 36.dp) = ProjectAvatar(name, modifier, size, cutout = colors.surface)
