package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.git.model.ErrorAction
import com.github.krpkbhdn.ccmanager.ui.git.model.ErrorPlan
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.changeSections
import com.github.krpkbhdn.ccmanager.ui.git.model.continueOperation
import com.github.krpkbhdn.ccmanager.ui.git.model.perform
import com.github.krpkbhdn.ccmanager.ui.git.model.push
import com.github.krpkbhdn.ccmanager.ui.git.model.requestAbort
import com.github.krpkbhdn.ccmanager.ui.git.model.skipCommit
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Everything that deserves attention above the tab content: a stopped operation, a failure, a missing identity, no upstream. */
@Composable
fun GitBanners(git: GitState) {
    val status = git.status ?: return
    Column {
        git.error?.let { ErrorBanner(it, git) }
        if (status.operation() != GitStatus.Operation.NONE) OperationBanner(status, git)
        val identity = git.identity
        if (identity != null && (identity.name().isNullOrBlank() || identity.email().isNullOrBlank())) {
            Strip(colors.warning, Icons.User, "Git does not know who you are", "Run git config --global user.name and user.email, then commit.") {}
        }
        if (status.repository() && status.upstream() == null && status.branch() != null && git.remotes.isNotEmpty() && !git.busy) {
            Strip(colors.accent, Icons.Push, "${status.branch()} is not published", "Push it to ${git.remotes.first().name()} and track it.") {
                AppButton("Publish branch", { git.push() }, compact = true, icon = Icons.Push)
            }
        }
    }
}

private fun operationTitle(operation: GitStatus.Operation) = when (operation) {
    GitStatus.Operation.MERGE -> "Merge in progress"
    GitStatus.Operation.REBASE -> "Rebase in progress"
    GitStatus.Operation.CHERRY_PICK -> "Cherry-pick in progress"
    GitStatus.Operation.REVERT -> "Revert in progress"
    GitStatus.Operation.NONE -> ""
}

@Composable
private fun OperationBanner(status: GitStatus, git: GitState) {
    val conflicts = changeSections(status.changes()).conflicts.size
    val detail = if (conflicts == 0) "All conflicts are resolved. Continue to finish." else "$conflicts ${if (conflicts == 1) "conflict" else "conflicts"} to resolve in the Changes tab."
    Strip(colors.warning, Icons.Merge, operationTitle(status.operation()), detail) {
        AppButton("Continue", { git.continueOperation() }, variant = ButtonVariant.PRIMARY, compact = true, enabled = conflicts == 0 && !git.busy)
        if (status.operation() == GitStatus.Operation.REBASE) AppButton("Skip", { git.skipCommit() }, compact = true, enabled = !git.busy)
        AppButton("Abort", { git.requestAbort() }, compact = true, enabled = !git.busy)
    }
}

@Composable
private fun ErrorBanner(plan: ErrorPlan, git: GitState) {
    Strip(colors.danger, Icons.Alert, "${plan.title}: ${plan.headline}", plan.hint.orEmpty(), onDismiss = { git.error = null }) {
        plan.actions.forEach { action -> AppButton(action.label, { git.perform(action) }, compact = true, variant = if (action == ErrorAction.FORCE_PUSH) ButtonVariant.DANGER else ButtonVariant.SECONDARY) }
    }
}

@Composable
private fun Strip(tint: Color, icon: ImageVector, title: String, detail: String, onDismiss: (() -> Unit)? = null, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = Alpha.SOFT)).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Glyph(icon, tint)
        Column(Modifier.weight(1f)) {
            Label(title, color = colors.textPrimary, style = Type.smallMedium, tooltipWhenTruncated = true)
            if (detail.isNotBlank()) Label(detail, color = colors.textSecondary, style = Type.caption, maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) { actions() }
        onDismiss?.let { IconButton(Icons.Close, it, tooltip = "Dismiss", size = 24.dp) }
    }
}
