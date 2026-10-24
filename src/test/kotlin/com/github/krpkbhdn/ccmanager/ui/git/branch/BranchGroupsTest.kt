package com.github.krpkbhdn.ccmanager.ui.git.branch

import com.github.krpkbhdn.ccmanager.git.GitBranch
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BranchGroupsTest {
    private fun branch(name: String, remote: Boolean = false) = GitBranch(name, remote, false, null, 0, 0, "abc1234", "subject", Instant.EPOCH)

    private val all = listOf(
        branch("main"), branch("feature/login"), branch("origin/main", true), branch("origin/feature/login", true),
        branch("origin/HEAD", true), branch("upstream/main", true),
    )

    @Test
    fun `remotes are grouped by name and the HEAD pointer is hidden`() {
        val groups = branchGroups(all, "")

        assertThat(groups.local.map { it.name() }).containsExactly("main", "feature/login")
        assertThat(groups.remotes.map { it.remote }).containsExactly("origin", "upstream")
        assertThat(groups.remotes.first().branches.map { it.name() }).containsExactly("origin/main", "origin/feature/login")
    }

    @Test
    fun `search matches fuzzily across both kinds and best first`() {
        val groups = branchGroups(all, "login")

        assertThat(groups.local.map { it.name() }).containsExactly("feature/login")
        assertThat(groups.remotes.single().branches.map { it.name() }).containsExactly("origin/feature/login")
        assertThat(branchGroups(all, "zzz").isEmpty).isTrue()
    }

    @Test
    fun `the flat list has a heading before every group`() {
        val items = branchListItems(branchGroups(all, ""))

        assertThat(items.filterIsInstance<BranchListItem.Heading>().map { it.title }).containsExactly("Local", "Remote / origin", "Remote / upstream")
        assertThat(items).hasSize(8)
    }

    @Test
    fun `remote names split into remote and short name`() {
        assertThat(remoteOf("origin/feature/x")).isEqualTo("origin")
        assertThat(shortName("origin/feature/x")).isEqualTo("feature/x")
    }

    @Test
    fun `branch names follow git's main rules`() {
        val existing = listOf("main")

        assertThat(branchNameProblem("feature/ok-1", existing)).isNull()
        assertThat(branchNameProblem("", existing)).isNotNull()
        assertThat(branchNameProblem("main", existing)).contains("already exists")
        listOf("-x", "a b", "a..b", "a~1", "a^", "a:b", "a?", "a*", "a[", "a\\b", "x.lock", "x.", "a//b", "a@{u}", "/a", "a/", "@", "HEAD")
            .forEach { assertThat(branchNameProblem(it, existing)).`as`(it).isNotNull() }
    }
}
