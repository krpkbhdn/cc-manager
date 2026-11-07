package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.project.Project

/** Foldable parts of the sidebar; the enum name is what [UiSettings] stores. */
enum class SidebarSection(val title: String) {
    LIBRARY("Library"),
    EXTENSIONS("Extensions"),
    PROJECTS("Projects"),
}

/** Projects in the order the sidebar lists them: each group, its members unless folded, then the ungrouped ones. */
fun sidebarProjectOrder(groups: List<Project>, membersOf: (Long) -> List<Project>, ungrouped: List<Project>, foldedGroups: Set<Long>): List<Project> =
    groups.flatMap { group -> if (group.id in foldedGroups) listOf(group) else listOf(group) + membersOf(group.id) } + ungrouped
