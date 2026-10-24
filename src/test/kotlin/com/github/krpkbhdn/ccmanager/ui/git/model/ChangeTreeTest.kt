package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChangeTreeTest {
    private fun change(path: String) = GitStatus.Change(path, null, Kind.NONE, Kind.MODIFIED)

    private val files = listOf("src/main/java/A.java", "src/main/java/B.java", "src/test/T.java", "README.md").map(::change)

    private fun describe(rows: List<ChangeRow>) = rows.map {
        when (it) {
            is ChangeRow.Folder -> "dir:${it.label}@${it.depth}(${it.fileCount})"
            is ChangeRow.File -> "file:${it.name}@${it.depth}"
        }
    }

    @Test
    fun `flat mode sorts by path and keeps the directory as a hint`() {
        val rows = changeRows(files, flat = true)

        assertThat(rows.map { (it as ChangeRow.File).change.path() }).containsExactly("README.md", "src/main/java/A.java", "src/main/java/B.java", "src/test/T.java")
        assertThat((rows[1] as ChangeRow.File).directory).isEqualTo("src/main/java")
    }

    @Test
    fun `tree mode joins single child folders into one label`() {
        assertThat(describe(changeRows(files, flat = false))).containsExactly(
            "dir:src@0(3)", "dir:main/java@1(2)", "file:A.java@2", "file:B.java@2", "dir:test@1(1)", "file:T.java@2", "file:README.md@0",
        )
    }

    @Test
    fun `collapsed folders hide their content`() {
        val rows = changeRows(files, flat = false, collapsedFolders = setOf("src/main/java"))

        assertThat(describe(rows)).doesNotContain("file:A.java@2")
        assertThat((rows[1] as ChangeRow.Folder).collapsed).isTrue()
    }

    @Test
    fun `paths under a folder exclude look alike siblings`() {
        val all = listOf("src/a.txt", "src2/b.txt", "src/x/c.txt").map(::change)

        assertThat(pathsUnder(all, "src")).containsExactly("src/a.txt", "src/x/c.txt")
    }

    @Test
    fun `items skip empty groups and fold collapsed ones to their heading`() {
        val sections = ChangeSections(emptyList(), listOf(change("a.txt")), listOf(change("b.txt"), change("c.txt")))

        val open = changeItems(sections, flat = true, collapsedGroups = emptySet(), collapsedFolders = emptySet())
        val folded = changeItems(sections, flat = true, collapsedGroups = setOf(ChangeGroup.UNSTAGED), collapsedFolders = emptySet())

        assertThat(open.map { it::class.simpleName }).containsExactly("Header", "File", "Header", "File", "File")
        assertThat(folded.map { it::class.simpleName }).containsExactly("Header", "File", "Header")
        assertThat((open[2] as ChangeItem.Header).count).isEqualTo(2)
    }

    @Test
    fun `folder collapse is remembered per group`() {
        val sections = ChangeSections(emptyList(), listOf(change("src/a.txt")), listOf(change("src/b.txt")))

        val items = changeItems(sections, flat = false, collapsedGroups = emptySet(), collapsedFolders = setOf(folderKey(ChangeGroup.STAGED, "src")))

        assertThat(items.filterIsInstance<ChangeItem.File>().map { it.group }).containsExactly(ChangeGroup.UNSTAGED)
    }
}
