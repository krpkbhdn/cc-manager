package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionScreen

/** The Skills screen of the sidebar: user-wide skills under a title. */
@Composable
fun SkillsScreen(state: SkillsState, modifier: Modifier = Modifier) =
    ExtensionScreen("Skills", "Instructions Claude loads on demand in every session of yours.", modifier) { SkillsPage(ExtensionContext.Global, state, it) }
