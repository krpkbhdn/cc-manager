package com.github.krpkbhdn.ccmanager.ui.git.branch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val pageWidth = 760.dp

/** The Branches section: the same list as the popup, on a page of its own. */
@Composable
fun BranchesView(git: GitState, modifier: Modifier = Modifier) {
    val query = rememberTextFieldState()
    Box(modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = pageWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                AppTextField(query, Modifier.weight(1f), placeholder = "Search branches", icon = Icons.Search)
                AppButton("New branch", { git.dialog = GitDialog.NewBranch(null) }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
            }
            BranchList(git, query.text.toString(), onPicked = {}, Modifier.weight(1f).fillMaxWidth().card())
        }
    }
}
