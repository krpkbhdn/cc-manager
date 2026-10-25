package com.github.krpkbhdn.ccmanager.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.theme.colors

fun ExtensionScope.label(): String = when (this) {
    ExtensionScope.USER -> "User"
    ExtensionScope.PROJECT -> "Project"
    ExtensionScope.LOCAL -> "Local"
}

fun ExtensionScope.description(): String = when (this) {
    ExtensionScope.USER -> "Every project of yours"
    ExtensionScope.PROJECT -> "Shared with the team through git"
    ExtensionScope.LOCAL -> "Only you, only in this project"
}

/** Wording for choosing where to install. */
fun ExtensionScope.choiceLabel(): String = when (this) {
    ExtensionScope.USER -> "For you, in every project"
    ExtensionScope.PROJECT -> "This project, shared through git"
    ExtensionScope.LOCAL -> "This project, only you"
}

@Composable
private fun ExtensionScope.tint(): Color = when (this) {
    ExtensionScope.USER -> colors.textSecondary
    ExtensionScope.PROJECT -> colors.accent
    ExtensionScope.LOCAL -> colors.warning
}

@Composable
fun ScopeBadge(scope: ExtensionScope, modifier: Modifier = Modifier) = Chip(scope.label(), scope.tint(), modifier)
