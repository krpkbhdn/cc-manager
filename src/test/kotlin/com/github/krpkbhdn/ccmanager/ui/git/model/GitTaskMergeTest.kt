package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitBranchService
import com.github.krpkbhdn.ccmanager.git.GitConflictService
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitIndexService
import com.github.krpkbhdn.ccmanager.git.GitProperties
import com.github.krpkbhdn.ccmanager.git.GitRunner
import com.github.krpkbhdn.ccmanager.git.GitStatusService
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Runs the merge flow against real temporary repositories: a main folder on `main` and a worktree on `task/x`. */
class GitTaskMergeTest {
    @TempDir
    lateinit var root: Path

    private lateinit var runner: GitRunner
    private lateinit var repo: Path
    private lateinit var wt: Path
    private lateinit var backend: GitBackend

    private fun git(dir: Path, vararg args: String) = runner.out(dir, *args).trim()

    private fun write(dir: Path, name: String, text: String) = Files.writeString(dir.resolve(name), text)

    private val form = TaskMergeForm("main", "task/x", "Add feature", "Merge task/x into main: Add feature", "Does the feature", true)

    @BeforeEach
    fun setUp() {
        Assumptions.assumeTrue(runCatching { ProcessBuilder("git", "--version").start().waitFor() == 0 }.getOrDefault(false), "git is not installed")
        val empty = Files.createFile(root.resolve("empty.gitconfig")).toString()
        runner = GitRunner(GitProperties.defaults(), mapOf("GIT_CONFIG_GLOBAL" to empty, "GIT_CONFIG_SYSTEM" to empty, "GIT_CONFIG_NOSYSTEM" to "1"))
        repo = Files.createDirectories(root.resolve("work"))
        git(repo, "init", "-b", "main")
        git(repo, "config", "user.name", "Test User")
        git(repo, "config", "user.email", "test@example.com")
        git(repo, "config", "commit.gpgsign", "false")
        git(repo, "config", "core.autocrlf", "false")
        write(repo, "README.md", "hello\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-m", "initial")
        wt = root.resolve("wt")
        git(repo, "worktree", "add", "-b", "task/x", wt.toString())
        val fake = FakeGit(FakeRepoData()).backend
        backend = GitBackend(GitStatusService(runner), GitIndexService(runner), GitBranchService(runner), fake.sync, fake.log, fake.stashes, fake.history, GitConflictService(runner), fake.worktrees, fake.watcher, fake.taskWorktrees)
    }

    private fun merge(f: TaskMergeForm = form) = backend.mergeTask(backend.inspectTaskMerge(wt, f)) {}

    @Test
    fun `dirty files are committed in the worktree then merged with a merge commit and nothing is pushed`() {
        write(wt, "feature.txt", "x\n")

        assertThat(merge()).isEqualTo("main")

        assertThat(git(wt, "log", "--format=%s", "-3")).contains("Add feature")
        assertThat(git(repo, "log", "-1", "--format=%B")).startsWith("Merge task/x into main: Add feature").contains("Does the feature")
        assertThat(git(repo, "rev-list", "--parents", "-n1", "HEAD").split(" ")).hasSize(3)
        assertThat(repo.resolve("feature.txt")).exists()
        assertThat(git(repo, "status", "--porcelain")).isEmpty()
        assertThat(git(repo, "remote")).isEmpty()
    }

    @Test
    fun `a clean worktree with commits merges without a commit of its own`() {
        write(wt, "feature.txt", "x\n")
        git(wt, "add", "-A")
        git(wt, "commit", "-m", "Add feature")
        val before = git(wt, "rev-parse", "HEAD")

        merge(form.copy(dirty = false))

        assertThat(git(wt, "rev-parse", "HEAD")).isEqualTo(before)
        assertThat(repo.resolve("feature.txt")).exists()
    }

    @Test
    fun `a base folder with tracked changes is refused and nothing changes`() {
        write(wt, "feature.txt", "x\n")
        write(repo, "README.md", "edited\n")
        val before = git(wt, "rev-parse", "HEAD")

        assertThatThrownBy { merge() }.isInstanceOf(GitException::class.java).hasMessageContaining("uncommitted changes")

        assertThat(git(wt, "rev-parse", "HEAD")).isEqualTo(before)
        assertThat(git(wt, "status", "--porcelain")).contains("feature.txt")
    }

    @Test
    fun `a base that is not checked out anywhere is refused`() {
        git(repo, "checkout", "-b", "other")
        write(wt, "feature.txt", "x\n")

        assertThatThrownBy { merge() }.hasMessageContaining("Check out main in the main folder first")
        assertThatThrownBy { merge(form.copy(base = "nope")) }.hasMessageContaining("Check out nope in the main folder first")
    }

    @Test
    fun `a conflict is aborted at once and the base is left unchanged`() {
        write(wt, "README.md", "from task\n")
        write(repo, "README.md", "from main\n")
        git(repo, "commit", "-am", "main edit")
        val baseHead = git(repo, "rev-parse", "HEAD")

        assertThatThrownBy { merge() }.isInstanceOf(GitException::class.java)
            .hasMessageContainingAll("Merge conflict in: README.md", "main is unchanged", "merge main into the task branch in the worktree")

        assertThat(git(repo, "rev-parse", "HEAD")).isEqualTo(baseHead)
        assertThat(git(repo, "status", "--porcelain")).isEmpty()
        assertThat(backend.branches.mergeInProgress(repo)).isFalse()
        assertThat(Files.readString(repo.resolve("README.md"))).isEqualTo("from main\n")
    }

    @Test
    fun `files changed after the confirmation abort before anything is committed or merged`() {
        write(wt, "feature.txt", "x\n")
        val confirmed = backend.inspectTaskMerge(wt, form)
        write(wt, "late.txt", "y\n")
        val before = git(wt, "rev-parse", "HEAD")
        val baseHead = git(repo, "rev-parse", "HEAD")

        assertThatThrownBy { backend.mergeTask(confirmed) {} }.hasMessageContaining("changed after the confirmation")

        assertThat(git(wt, "rev-parse", "HEAD")).isEqualTo(before)
        assertThat(git(repo, "rev-parse", "HEAD")).isEqualTo(baseHead)
    }

    @Test
    fun `a branch that already moved after the confirmation is refused`() {
        write(wt, "feature.txt", "x\n")
        git(wt, "add", "-A")
        git(wt, "commit", "-m", "one")
        val confirmed = backend.inspectTaskMerge(wt, form.copy(dirty = false))
        git(wt, "commit", "--allow-empty", "-m", "two")

        assertThatThrownBy { backend.mergeTask(confirmed) {} }.hasMessageContaining("changed after the confirmation")
        assertThat(repo.resolve("feature.txt")).doesNotExist()
    }

    @Test
    fun `a branch already merged or without commits has nothing to merge`() {
        assertThatThrownBy { merge(form.copy(dirty = false)) }.hasMessageContaining("Nothing to merge")

        write(wt, "feature.txt", "x\n")
        merge()

        assertThatThrownBy { merge(form.copy(dirty = false)) }.hasMessageContaining("Nothing to merge")
    }

    @Test
    fun `a merge in progress in the base folder is refused`() {
        write(wt, "feature.txt", "x\n")
        git(repo, "checkout", "-b", "side")
        write(repo, "README.md", "side\n")
        git(repo, "commit", "-am", "side")
        git(repo, "checkout", "main")
        write(repo, "README.md", "main\n")
        git(repo, "commit", "-am", "main")
        runCatching { git(repo, "merge", "side") }

        assertThatThrownBy { merge() }.hasMessageContaining("in progress")
    }

    @Test
    fun `a task worktree with an unfinished merge or conflicted files is refused before anything is committed`() {
        write(wt, "README.md", "task\n")
        git(wt, "commit", "-am", "task edit")
        write(repo, "README.md", "main\n")
        git(repo, "commit", "-am", "main edit")
        runCatching { git(wt, "merge", "main") }
        write(wt, "other.txt", "x\n")
        val before = git(wt, "rev-parse", "HEAD")

        assertThatThrownBy { merge() }.hasMessageContaining("in progress")

        assertThat(git(wt, "rev-parse", "HEAD")).isEqualTo(before)
        assertThat(git(wt, "diff", "--cached", "--name-only")).doesNotContain("other.txt")
    }

    @Test
    fun `a failed merge that is not a conflict says the dirty files stay committed in the worktree`() {
        write(wt, "feature.txt", "x\n")
        val confirmed = backend.inspectTaskMerge(wt, form)
        write(repo, "feature.txt", "untracked in base\n")

        assertThatThrownBy { backend.mergeTask(confirmed) {} }.hasMessageContainingAll("committed in the task worktree", "main is unchanged")
    }

    @Test
    fun `the task branch cannot be merged into itself`() {
        assertThat(form.copy(base = "task/x").problem()).isEqualTo("The task branch cannot be merged into itself")
        assertThat(form.copy(commitMessage = " ").problem()).isEqualTo("Commit message is empty")
        assertThat(form.copy(commitMessage = " ", dirty = false).problem()).isNull()
        assertThat(form.copy(mergeTitle = "a\nb").problem()).isNotNull()
        assertThat(form.copy(base = "--x").problem()).isNotNull()
    }

    @Test
    fun `confirm text lists files, folders, commits, messages and that nothing is pushed`() {
        val text = taskMergeConfirmText(form, Path.of("wt"), Path.of("main-folder"), listOf("a.txt", "b/c.kt"), listOf("Second", "First"), emptyList())

        assertThat(text).contains("In wt: git add the 2 listed file(s)", "message: Add feature", "     a.txt", "     b/c.kt",
            "In main-folder (where main is checked out): git merge --no-ff task/x, bringing in 2 commit(s) plus the commit above", "     Second", "     First",
            "Merge task/x into main: Add feature", "Does the feature", "Nothing is pushed.").doesNotContain("WARNING")
    }

    @Test
    fun `confirm text warns about secrets and active sessions and skips the commit step when clean`() {
        val text = taskMergeConfirmText(form, Path.of("wt"), Path.of("m"), listOf(".env"), emptyList(), listOf(Path.of("wt")))
        assertThat(text).contains("WARNING: these files look like secrets: .env", "WARNING: a session or run is active in wt")

        val clean = taskMergeConfirmText(form.copy(dirty = false), Path.of("wt"), Path.of("m"), emptyList(), listOf("One"), emptyList())
        assertThat(clean).doesNotContain("git add", "plus the commit above").startsWith("1. In m")
    }
}
