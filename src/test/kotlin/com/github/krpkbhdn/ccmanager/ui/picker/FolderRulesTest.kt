package com.github.krpkbhdn.ccmanager.ui.picker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FolderRulesTest {
    @TempDir
    lateinit var root: Path

    private fun folder(name: String): Path = Files.createDirectories(root.resolve(name))

    private fun entries(vararg names: String) = names.map { FolderEntry(root.resolve(it), it, it.startsWith(".")) }

    @Test
    fun sortsCaseInsensitivelyAndCollapsesHiddenFolders() {
        val all = entries("beta", "Alpha", ".git", "charlie")

        assertThat(FolderRules.visible(all, showHidden = false, query = "").map { it.name }).containsExactly("Alpha", "beta", "charlie")
        assertThat(FolderRules.visible(all, showHidden = true, query = "").map { it.name }).containsExactly(".git", "Alpha", "beta", "charlie")
    }

    @Test
    fun filtersByCaseInsensitiveSubstring() {
        val all = entries("demo-api", "Demo-web", "notes")

        assertThat(FolderRules.visible(all, showHidden = false, query = " DEMO ").map { it.name }).containsExactly("demo-api", "Demo-web")
    }

    @Test
    fun listsOnlyDirectoriesAndFlagsDotFoldersAsHidden() {
        folder("src")
        folder(".cache")
        Files.createFile(root.resolve("readme.md"))

        val listing = FolderRules.listFolders(root) as Listing.Loaded

        assertThat(listing.folders.associate { it.name to it.hidden }).containsExactlyInAnyOrderEntriesOf(mapOf("src" to false, ".cache" to true))
    }

    @Test
    fun reportsMissingDirectory() {
        assertThat(FolderRules.listFolders(root.resolve("nope"))).isEqualTo(Listing.Missing)
    }

    @Test
    fun detectsProjectBadges() {
        val project = folder("project")
        Files.createDirectories(project.resolve(".git"))
        Files.createFile(project.resolve("CLAUDE.md"))
        Files.createFile(project.resolve("build.gradle.kts"))

        assertThat(FolderRules.badges(project)).containsExactlyInAnyOrder(ProjectBadge.Git, ProjectBadge.Claude, ProjectBadge.Build)
        assertThat(FolderRules.badges(folder("plain"))).isEmpty()
    }

    @Test
    fun claudeDirectoryAloneIsAClaudeBadge() {
        val project = folder("p")
        Files.createDirectories(project.resolve(".claude"))

        assertThat(FolderRules.badges(project)).containsExactly(ProjectBadge.Claude)
    }

    @Test
    fun checksPathStates() {
        val file = Files.createFile(root.resolve("a.txt"))

        assertThat(FolderRules.check(root)).isEqualTo(PathCheck.Valid)
        assertThat(FolderRules.check(root.resolve("missing"))).isEqualTo(PathCheck.NotFound)
        assertThat(FolderRules.check(file)).isEqualTo(PathCheck.NotDirectory)
        assertThat(FolderRules.check(null)).isEqualTo(PathCheck.Blank)
        assertThat(FolderRules.checkText("   ")).isEqualTo(PathCheck.Blank)
        assertThat(FolderRules.checkText(root.toString())).isEqualTo(PathCheck.Valid)
    }

    @Test
    fun buildsBreadcrumbsFromRootToLeaf() {
        val crumbs = FolderRules.breadcrumbs(root.resolve("a").resolve("b"))

        assertThat(crumbs.last().label).isEqualTo("b")
        assertThat(crumbs.last().path).isEqualTo(root.resolve("a").resolve("b"))
        assertThat(crumbs[crumbs.size - 2].path).isEqualTo(root.resolve("a"))
        assertThat(crumbs.first().path).isEqualTo(root.root)
    }

    @Test
    fun collapsesLongTrailKeepingFirstAndLastCrumbs() {
        val crumbs = (1..8).map { Crumb("c$it", root.resolve("c$it")) }

        val collapsed = FolderRules.collapse(crumbs, maxVisible = 5)

        assertThat(collapsed.map { it.label }).containsExactly("c1", "…", "c6", "c7", "c8")
        assertThat(collapsed[1].path).isEqualTo(crumbs[4].path)
        assertThat(FolderRules.collapse(crumbs.take(4), maxVisible = 5)).hasSize(4)
    }

    @Test
    fun suggestsChildDirectoriesMatchingTypedPrefix() {
        folder("alpha")
        folder("Alps")
        folder("beta")
        folder(".alpine")

        val byPrefix = FolderRules.suggestions(root.resolve("al").toString()).map { it.fileName.toString() }
        val afterSeparator = FolderRules.suggestions(root.toString() + root.fileSystem.separator).map { it.fileName.toString() }

        assertThat(byPrefix).containsExactly("alpha", "Alps")
        assertThat(afterSeparator).containsExactly("alpha", "Alps", "beta")
    }

    @Test
    fun rejectsInvalidNewFolderNames() {
        folder("taken")

        assertThat(FolderRules.newFolderError(root, "")).isNotNull()
        assertThat(FolderRules.newFolderError(root, "..")).isNotNull()
        assertThat(FolderRules.newFolderError(root, "a/b")).isNotNull()
        assertThat(FolderRules.newFolderError(root, "bad:name")).isNotNull()
        assertThat(FolderRules.newFolderError(root, "trailing.")).isNotNull()
        assertThat(FolderRules.newFolderError(root, "taken")).contains("already exists")
        assertThat(FolderRules.newFolderError(root, "fresh-name")).isNull()
    }

    @Test
    fun createsFolderWithTrimmedName() {
        val created = FolderRules.createFolder(root, "  made  ").getOrThrow()

        assertThat(created).isEqualTo(root.resolve("made")).isDirectory()
    }
}
