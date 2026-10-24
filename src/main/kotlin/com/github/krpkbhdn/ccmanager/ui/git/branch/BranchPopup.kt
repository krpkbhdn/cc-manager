package com.github.krpkbhdn.ccmanager.ui.git.branch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val popupWidth = 400.dp
private val listHeight = 380.dp

/** IDE-style branch switcher under the toolbar chip: search, grouped list, "New branch..." in the footer. */
@Composable
fun BranchPopup(git: GitState, onDismiss: () -> Unit) {
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AnchoredPopup(onDismiss, modifier = Modifier.width(popupWidth)) {
        Column {
            AppTextField(query, Modifier.fillMaxWidth().padding(Spacing.sm), placeholder = "Search branches", icon = Icons.Search, focusRequester = focus)
            HorizontalRule()
            BranchList(git, query.text.toString(), onDismiss, Modifier.heightIn(max = listHeight))
            HorizontalRule()
            Row(Modifier.fillMaxWidth().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("New branch...", { onDismiss(); git.dialog = GitDialog.NewBranch(null) }, icon = Icons.Plus)
                GhostButton("All branches", { onDismiss(); git.show(GitSubView.BRANCHES) }, icon = Icons.Layers)
            }
        }
    }
}
