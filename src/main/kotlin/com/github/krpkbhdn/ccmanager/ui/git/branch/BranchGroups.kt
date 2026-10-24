package com.github.krpkbhdn.ccmanager.ui.git.branch

import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.ui.state.fuzzyFilter

/** Branches of one remote, with names as listed ("origin/main"); [remote] is the remote's name. */
data class RemoteGroup(val remote: String, val branches: List<GitBranch>)

/** The popup's content after filtering: local branches, then remote branches grouped by remote. */
data class BranchGroups(val local: List<GitBranch>, val remotes: List<RemoteGroup>) {
    val isEmpty: Boolean get() = local.isEmpty() && remotes.isEmpty()
}

fun branchGroups(branches: List<GitBranch>, query: String): BranchGroups {
    val matching = if (query.isBlank()) branches else fuzzyFilter(query, branches) { it.name }
    val remotes = matching.filter { it.remote && !it.name.endsWith("/HEAD") }
        .groupBy { remoteOf(it.name) }
        .map { (remote, group) -> RemoteGroup(remote, group) }
    return BranchGroups(matching.filter { !it.remote }, remotes)
}

/** A line of the branch list: a group heading or a branch. */
sealed interface BranchListItem {
    data class Heading(val title: String, val count: Int) : BranchListItem

    data class Entry(val branch: GitBranch) : BranchListItem
}

fun branchListItems(groups: BranchGroups): List<BranchListItem> = buildList {
    if (groups.local.isNotEmpty()) {
        add(BranchListItem.Heading("Local", groups.local.size))
        groups.local.forEach { add(BranchListItem.Entry(it)) }
    }
    groups.remotes.forEach { group ->
        add(BranchListItem.Heading("Remote / ${group.remote}", group.branches.size))
        group.branches.forEach { add(BranchListItem.Entry(it)) }
    }
}

fun remoteOf(remoteBranch: String): String = remoteBranch.substringBefore('/')

/** The branch name without its remote, "origin/feature/x" -> "feature/x". */
fun shortName(remoteBranch: String): String = remoteBranch.substringAfter('/')

private val forbiddenInBranchName = Regex("[\\s~^:?*\\[\\\\]|\\.\\.|@\\{|//")

/** Why [name] cannot be a branch name, or null when git would accept it. Mirrors git check-ref-format's main rules. */
fun branchNameProblem(name: String, existing: Collection<String>): String? = when {
    name.isBlank() -> "Enter a name"
    name in existing -> "A branch with this name already exists"
    name.startsWith("-") || name.startsWith("/") || name.endsWith("/") -> "Cannot start with - or / or end with /"
    name.endsWith(".") || name.endsWith(".lock") -> "Cannot end with . or .lock"
    forbiddenInBranchName.containsMatchIn(name) -> "Contains characters git does not allow"
    name == "@" || name == "HEAD" -> "Reserved name"
    else -> null
}
